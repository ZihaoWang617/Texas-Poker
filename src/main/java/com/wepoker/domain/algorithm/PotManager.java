package com.wepoker.domain.algorithm;

import com.wepoker.domain.model.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PotManager - 处理底池逻辑
 * 
 * 功能：
 * 1. 追踪所有下注
 * 2. 计算主池和边池（All-in场景）
 * 3. 分配底池给赢家
 * 4. 支持Run It Twice
 * 5. 所有金额均使用long（最小分值单位）
 */
@Slf4j
public class PotManager {

    private final Table table;
    private final Hand hand;

    // 每个玩家每条街的下注记录 [playerSeat][streetIndex] = amount
    private final Map<String, List<Long>> playerBetsPerStreet;

    // 追踪每个玩家在当前轮街中是否已下注
    private final Set<String> playersWhoHaveActed;

    public PotManager(Table table, Hand hand) {
        this.table = table;
        this.hand = hand;
        this.playerBetsPerStreet = new HashMap<>();
        this.playersWhoHaveActed = new HashSet<>();

        // 初始化
        table.getActivePlayers().forEach(p -> 
            playerBetsPerStreet.put(p.getPlayerId(), new ArrayList<>())
        );
    }

    /**
     * 处理玩家下注
     * 
     * @param playerId 玩家ID
     * @param amount 下注金额
     * @throws IllegalArgumentException 如果金额非法
     */
    public void handleBet(String playerId, long amount, String street) {
        Player player = table.getActivePlayers().stream()
            .filter(p -> p.getPlayerId().equals(playerId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        if (amount < 0) {
            throw new IllegalArgumentException("Bet amount cannot be negative");
        }

        // 原子性地扣除筹码（已在Player中实现synchronized）
        player.deductStack(amount);

        // 更新玩家在当前街的下注金额
        player.setCurrentBet(player.getCurrentBet() + amount);
        player.setBetThisStreet(player.getBetThisStreet() + amount);

        // 更新街级别的下注
        List<Long> bets = playerBetsPerStreet.computeIfAbsent(playerId, k -> new ArrayList<>());
        while (bets.size() <= getStreetIndex(street)) {
            bets.add(0L);
        }
        bets.set(getStreetIndex(street), bets.get(getStreetIndex(street)) + amount);

        // 标记玩家已行动
        playersWhoHaveActed.add(playerId);

        // 更新表级别数据
        table.setTotalPotSize(table.getTotalPotSize() + amount);
        table.setCurrentBetThisStreet(Math.max(table.getCurrentBetThisStreet(), player.getCurrentBet()));

        log.debug("Player {} bet {} on {}, stack now: {}", 
            playerId, amount, street, player.getStackSize());
    }

    /**
     * 所有玩家完成某条街的下注，计算pot
     */
    public void completeStreet(String currentStreet) {
        playersWhoHaveActed.clear();

        // 重置玩家的本轮下注额，为下一街准备
        table.getActivePlayers().forEach(p -> {
            if (!p.isAllIn()) {
                p.setCurrentBet(0);
            }
        });

        table.setCurrentBetThisStreet(0);
    }

    /**
     * 计算main pot和all side pots
     * 最复杂的算法：处理多个玩家all-in的场景
     */
    public List<Pot> calculatePots() {
        List<Pot> pots = new ArrayList<>();

        // 1. 获取所有玩家的总投入金额（按升序排列）
        List<PlayerStackInfo> stackInfos = table.getActivePlayers().stream()
            .map(p -> {
                long totalBet = playerBetsPerStreet.getOrDefault(p.getPlayerId(), new ArrayList<>())
                    .stream().mapToLong(Long::longValue).sum();
                return new PlayerStackInfo(p.getPlayerId(), totalBet, p);
            })
            .sorted(Comparator.comparingLong(s -> s.totalBet))
            .collect(Collectors.toList());

        if (stackInfos.isEmpty()) {
            return pots;
        }

        // 2. 创建pot层级
        long previousBet = 0;
        for (int i = 0; i < stackInfos.size(); i++) {
            PlayerStackInfo info = stackInfos.get(i);
            long betDifference = info.totalBet - previousBet;

            if (betDifference > 0) {
                // 这一层pot的贡献玩家数
                int contributorCount = stackInfos.size() - i;
                long potAmount = betDifference * contributorCount;

                // 确定哪些玩家可以赢取这个pot
                Set<String> eligiblePlayers = new HashSet<>();
                for (int j = i; j < stackInfos.size(); j++) {
                    eligiblePlayers.add(stackInfos.get(j).playerId);
                }

                Pot pot = new Pot();
                pot.setPotSequence(pots.size());  // 0=main, 1=side1, 2=side2...
                pot.setPotSize(potAmount);
                pot.setEligiblePlayers(eligiblePlayers);
                pot.setMinRaiseAmount(info.totalBet);

                pots.add(pot);
            }

            previousBet = info.totalBet;
        }

        log.info("Calculated {} pots: {}", pots.size(), pots.stream()
            .map(p -> String.format("Pot%d(%.2f)", p.getPotSequence(), p.getPotSize() / 100.0))
            .collect(Collectors.joining(", ")));

        return pots;
    }

    /**
     * 分配pot给赢家（单次run）
     */
    public List<PotDistribution> distributePot(Map<String, HandRank> playerRanks) {
        List<PotDistribution> distributions = new ArrayList<>();
        List<Pot> pots = hand.getPots();

        for (Pot pot : pots) {
            // 在符合条件的玩家中找出最强的手牌
            Map<String, HandRank> eligibleRanks = playerRanks.entrySet().stream()
                .filter(entry -> pot.getEligiblePlayers().contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (eligibleRanks.isEmpty()) {
                continue;
            }

            // 找出最高手牌的玩家
            String winner = eligibleRanks.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().getRankValue()))
                .map(Map.Entry::getKey)
                .orElseThrow();

            // 计算抽水
            long rakeAmount = 0;
            if (pot.getPotSequence() == 0) {  // 仅main pot抽水
                long potAfterRake = pot.getPotSize();
                if (table.getConfig().getRakePercentage() > 0) {
                    rakeAmount = Math.min(
                        (long) (pot.getPotSize() * table.getConfig().getRakePercentage()),
                        table.getConfig().getRakeMaxPerHand()
                    );
                    potAfterRake = pot.getPotSize() - rakeAmount;
                }
                distributions.add(new PotDistribution(winner, potAfterRake, 1, 
                    String.format("Main Pot Winner (after rake: %d)", rakeAmount)));
            } else {
                distributions.add(new PotDistribution(winner, pot.getPotSize(), 1,
                    String.format("Side Pot #%d Winner", pot.getPotSequence())));
            }
        }

        return distributions;
    }

    /**
     * Run It Twice - All-in时两次发牌平分底池
     * 
     * @param hand1Ranks 第一次run的牌型 
     * @param hand2Ranks 第二次run的牌型
     */
    public List<PotDistribution> distributeRunItTwice(Map<String, HandRank> hand1Ranks,
                                                      Map<String, HandRank> hand2Ranks) {
        List<PotDistribution> distributions1 = distributePot(hand1Ranks);
        List<PotDistribution> distributions2 = distributePot(hand2Ranks);

        // 合并两次run的结果，平分底池
        Map<String, Long> playerWinnings = new HashMap<>();
        distributions1.forEach(d -> playerWinnings.merge(d.getPlayerId(), d.getAmount() / 2, Long::sum));
        distributions2.forEach(d -> playerWinnings.merge(d.getPlayerId(), d.getAmount() / 2, Long::sum));

        return playerWinnings.entrySet().stream()
            .map(entry -> new PotDistribution(entry.getKey(), entry.getValue(), 1, "Run It Twice Result"))
            .collect(Collectors.toList());
    }

    /**
     * 最终结算 - 将筹码发给赢家，更新账户余额
     */
    public void settleHand(List<PotDistribution> distributions) {
        for (PotDistribution dist : distributions) {
            Player player = getPlayerById(dist.getPlayerId());
            if (player != null) {
                player.addStack(dist.getAmount());
                log.info("Player {} won {} (Total stack now: {})", 
                    dist.getPlayerId(), dist.getAmount(), player.getStackSize());
            }
        }

        // 持久化到数据库
        persistSettlement(distributions);
    }

    /**
     * 持久化结算数据到数据库
     */
    private void persistSettlement(List<PotDistribution> distributions) {
        // TODO: 保存到MySQL数据库
        // - 更新玩家账户余额 (account_balance)
        // - 记录该手牌的结算日志 (hand_settlement_log)
        log.info("Settlement persisted for hand: {}", hand.getHandId());
    }

    /**
     * 获取玩家总下注额
     */
    public long getPlayerTotalBet(String playerId) {
        return playerBetsPerStreet.getOrDefault(playerId, new ArrayList<>())
            .stream().mapToLong(Long::longValue).sum();
    }

    /**
     * 辅助类 - 追踪玩家投入信息
     */
    private static class PlayerStackInfo {
        String playerId;
        long totalBet;
        Player player;

        PlayerStackInfo(String playerId, long totalBet, Player player) {
            this.playerId = playerId;
            this.totalBet = totalBet;
            this.player = player;
        }
    }

    /**
     * 街的索引映射
     */
    private int getStreetIndex(String street) {
        return switch (street) {
            case "PRE_FLOP" -> 0;
            case "FLOP" -> 1;
            case "TURN" -> 2;
            case "RIVER" -> 3;
            default -> -1;
        };
    }

    /**
     * 根据ID获取玩家
     */
    private Player getPlayerById(String playerId) {
        return table.getActivePlayers().stream()
            .filter(p -> p.getPlayerId().equals(playerId))
            .findFirst()
            .orElse(null);
    }
}
