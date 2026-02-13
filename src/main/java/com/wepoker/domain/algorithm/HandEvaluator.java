package com.wepoker.domain.algorithm;

import com.wepoker.domain.model.Card;
import com.wepoker.domain.model.HandRank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * HandEvaluator - 7选5最高牌型评估
 */
public class HandEvaluator {

    /**
     * 7张牌（2张底牌 + 5张公共牌）的组合评估
     */
    public static HandRank evaluateSevenCards(Card[] sevenCards) {
        if (sevenCards == null || sevenCards.length != 7) {
            throw new IllegalArgumentException("Must have exactly 7 cards");
        }

        HandRank bestRank = null;
        long bestRankValue = Long.MAX_VALUE;

        // 遍历所有 C(7,5)=21 种组合
        for (int[] combo : generateCombinations(7, 5)) {
            Card[] fiveCards = new Card[5];
            for (int i = 0; i < 5; i++) {
                fiveCards[i] = sevenCards[combo[i]];
            }

            HandRank rank = evaluateFiveCards(fiveCards);
            if (rank.getRankValue() < bestRankValue) {
                bestRankValue = rank.getRankValue();
                bestRank = rank;
            }
        }

        return bestRank != null ? bestRank : new HandRank(9999999, -1, "No Hand", new ArrayList<>());
    }

    /**
     * 评估5张牌的牌型。
     * rankValue 越小表示牌力越强。
     */
    private static HandRank evaluateFiveCards(Card[] fiveCards) {
        if (fiveCards == null || fiveCards.length != 5) {
            throw new IllegalArgumentException("Must have exactly 5 cards");
        }
        for (Card card : fiveCards) {
            if (card == null) {
                throw new IllegalArgumentException("Card cannot be null");
            }
        }

        int[] rankCounts = new int[15]; // 2..14
        int[] suitCounts = new int[4];  // 0..3
        for (Card card : fiveCards) {
            rankCounts[card.getRank().getValue()]++;
            suitCounts[card.getSuit().getValue()]++;
        }

        boolean isFlush = Arrays.stream(suitCounts).anyMatch(count -> count == 5);
        int straightHigh = getStraightHigh(rankCounts); // -1 表示非顺子
        boolean isStraight = straightHigh != -1;

        HandType handType = determineHandType(rankCounts, isStraight, isFlush);
        long rankValue = calculateRankValue(handType, rankCounts, straightHigh);

        return new HandRank((int) rankValue, handType.getLevel(), handType.getDescription(), Arrays.asList(fiveCards));
    }

    private static HandType determineHandType(int[] rankCounts, boolean isStraight, boolean isFlush) {
        if (isStraight && isFlush) {
            return HandType.STRAIGHT_FLUSH;
        }

        int pairs = 0;
        boolean hasThree = false;
        boolean hasFour = false;

        for (int rank = 2; rank <= 14; rank++) {
            int count = rankCounts[rank];
            if (count == 4) {
                hasFour = true;
            } else if (count == 3) {
                hasThree = true;
            } else if (count == 2) {
                pairs++;
            }
        }

        if (hasFour) {
            return HandType.FOUR_OF_A_KIND;
        }
        if (hasThree && pairs == 1) {
            return HandType.FULL_HOUSE;
        }
        if (isFlush) {
            return HandType.FLUSH;
        }
        if (isStraight) {
            return HandType.STRAIGHT;
        }
        if (hasThree) {
            return HandType.THREE_OF_A_KIND;
        }
        if (pairs == 2) {
            return HandType.TWO_PAIR;
        }
        if (pairs == 1) {
            return HandType.PAIR;
        }
        return HandType.HIGH_CARD;
    }

    /**
     * 返回顺子最高点（A2345 返回 5），无顺子返回 -1。
     */
    private static int getStraightHigh(int[] rankCounts) {
        // 普通顺子：A 当高牌
        for (int high = 14; high >= 5; high--) {
            boolean ok = true;
            for (int r = high; r > high - 5; r--) {
                if (rankCounts[r] == 0) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return high;
            }
        }

        // 轮牌 A-2-3-4-5
        if (rankCounts[14] > 0 && rankCounts[2] > 0 && rankCounts[3] > 0 && rankCounts[4] > 0 && rankCounts[5] > 0) {
            return 5;
        }

        return -1;
    }

    /**
     * 牌型枚举（level 越大越强）
     */
    public enum HandType {
        STRAIGHT_FLUSH(8, "同花顺"),
        FOUR_OF_A_KIND(7, "四条"),
        FULL_HOUSE(6, "葫芦"),
        FLUSH(5, "同花"),
        STRAIGHT(4, "顺子"),
        THREE_OF_A_KIND(3, "三条"),
        TWO_PAIR(2, "两对"),
        PAIR(1, "一对"),
        HIGH_CARD(0, "高牌");

        private final int level;
        private final String description;

        HandType(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * 计算 rank value 用于比较。
     * category 越小越强（0=同花顺，8=高牌），同 category 下 tie 值越小越强。
     */
    private static long calculateRankValue(HandType handType, int[] rankCounts, int straightHigh) {
        int category = 8 - handType.getLevel();
        List<Integer> tieBreakRanks = buildTieBreakRanks(handType, rankCounts, straightHigh);
        long tieValue = encodeTieBreak(tieBreakRanks);
        return category * 1_000_000L + tieValue;
    }

    private static List<Integer> buildTieBreakRanks(HandType handType, int[] rankCounts, int straightHigh) {
        List<Integer> result = new ArrayList<>();

        switch (handType) {
            case STRAIGHT_FLUSH:
            case STRAIGHT:
                result.add(straightHigh);
                break;

            case FOUR_OF_A_KIND: {
                int quad = rankWithCount(rankCounts, 4, true).get(0);
                int kicker = rankWithCount(rankCounts, 1, true).get(0);
                result.add(quad);
                result.add(kicker);
                break;
            }

            case FULL_HOUSE: {
                int trips = rankWithCount(rankCounts, 3, true).get(0);
                int pair = rankWithCount(rankCounts, 2, true).get(0);
                result.add(trips);
                result.add(pair);
                break;
            }

            case FLUSH:
            case HIGH_CARD:
                result.addAll(rankWithCount(rankCounts, 1, true));
                break;

            case THREE_OF_A_KIND: {
                int trips = rankWithCount(rankCounts, 3, true).get(0);
                result.add(trips);
                result.addAll(rankWithCount(rankCounts, 1, true));
                break;
            }

            case TWO_PAIR: {
                List<Integer> pairs = rankWithCount(rankCounts, 2, true);
                int kicker = rankWithCount(rankCounts, 1, true).get(0);
                result.addAll(pairs); // 高对在前
                result.add(kicker);
                break;
            }

            case PAIR: {
                int pair = rankWithCount(rankCounts, 2, true).get(0);
                result.add(pair);
                result.addAll(rankWithCount(rankCounts, 1, true));
                break;
            }

            default:
                break;
        }

        return result;
    }

    private static List<Integer> rankWithCount(int[] rankCounts, int targetCount, boolean desc) {
        List<Integer> ranks = new ArrayList<>();
        for (int rank = 2; rank <= 14; rank++) {
            if (rankCounts[rank] == targetCount) {
                ranks.add(rank);
            }
        }
        if (desc) {
            ranks.sort(Collections.reverseOrder());
        } else {
            Collections.sort(ranks);
        }
        return ranks;
    }

    /**
     * 把 tie-break 列表编码为单调可比较值。
     * rank 越大牌力越强，因此编码时转换为 (14-rank)，使其“越强越小”。
     */
    private static long encodeTieBreak(List<Integer> ranks) {
        long value = 0;
        for (Integer rank : ranks) {
            value = value * 15 + (14 - rank);
        }
        return value;
    }

    /**
     * 生成 C(n, k) 的所有组合
     */
    private static int[][] generateCombinations(int n, int k) {
        List<int[]> combinations = new ArrayList<>();
        int[] combination = new int[k];
        generateCombinationsHelper(combinations, combination, 0, 0, n, k);
        return combinations.toArray(new int[0][]);
    }

    private static void generateCombinationsHelper(List<int[]> combinations,
                                                   int[] combination,
                                                   int start,
                                                   int index,
                                                   int n,
                                                   int k) {
        if (index == k) {
            combinations.add(combination.clone());
            return;
        }

        for (int i = start; i < n; i++) {
            combination[index] = i;
            generateCombinationsHelper(combinations, combination, i + 1, index + 1, n, k);
        }
    }

    /**
     * @return 正数表示 hand1 更强，负数表示 hand2 更强，0 表示平手
     */
    public static int compareHands(HandRank hand1, HandRank hand2) {
        return Integer.compare(hand2.getRankValue(), hand1.getRankValue());
    }
}
