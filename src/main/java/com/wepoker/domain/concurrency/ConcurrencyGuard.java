package com.wepoker.domain.concurrency;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ConcurrencyGuard - 确保高并发下的筹码安全
 * 
 * 问题述：
 * 在高并发环境中（如数百个玩家同时操作），可能出现：
 * 1. 扣除筹码时出现负值（oversell）
 * 2. 金额不匹配
 * 3. 并发修改导致的不一致
 * 
 * 解决方案：
 * 1. 使用synchronized关键字（已在Player类中实现）
 * 2. 使用AtomicLong确保原子性
 * 3. 使用ReadWriteLock支持高并发读取
 * 4. 使用version字段实现乐观锁
 */
@Slf4j
public class ConcurrencyGuard {

    /**
     * 玩家筹码的线程安全容器
     */
    public static class ThreadSafeStack {
        private final String playerId;
        private volatile long stackSize;
        private final AtomicLong version = new AtomicLong(0);
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        public ThreadSafeStack(String playerId, long initialStack) {
            this.playerId = playerId;
            this.stackSize = initialStack;
        }

        /**
         * 原子性地扣除筹码（使用write lock）
         */
        public boolean deduct(long amount) {
            lock.writeLock().lock();
            try {
                if (stackSize < amount) {
                    log.error("Insufficient stack for player {}: {} < {}", 
                        playerId, stackSize, amount);
                    return false;
                }
                
                stackSize -= amount;
                version.incrementAndGet();
                
                log.debug("Deducted {} from player {}, remaining: {}", 
                    amount, playerId, stackSize);
                return true;
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * 原子性地增加筹码
         */
        public void add(long amount) {
            lock.writeLock().lock();
            try {
                if (amount < 0) {
                    throw new IllegalArgumentException("Cannot add negative amount");
                }
                stackSize += amount;
                version.incrementAndGet();
                
                log.debug("Added {} to player {}, total: {}", 
                    amount, playerId, stackSize);
            } finally {
                lock.writeLock().unlock();
            }
        }

        /**
         * 非阻塞读取筹码（使用read lock）
         * 支持高并发读取
         */
        public long getStack() {
            lock.readLock().lock();
            try {
                return stackSize;
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * 获取当前版本号（用于乐观锁）
         */
        public long getVersion() {
            return version.get();
        }

        /**
         * 比较并交换（CAS操作）- 用于乐观锁场景
         */
        public boolean compareAndSwap(long expectedVersion, long newStack) {
            lock.writeLock().lock();
            try {
                if (version.get() != expectedVersion) {
                    return false;
                }
                this.stackSize = newStack;
                this.version.incrementAndGet();
                return true;
            } finally {
                lock.writeLock().unlock();
            }
        }

        @Override
        public String toString() {
            return String.format("Stack[player=%s, size=%d, version=%d]", 
                playerId, getStack(), getVersion());
        }
    }

    /**
     * 交易日志 - 记录每一次筹码操作，用于审计和恢复
     */
    @Slf4j
    public static class TransactionLog {
        private final String playerId;
        private final List<Transaction> transactions = Collections.synchronizedList(new ArrayList<>());
        private final AtomicLong balance = new AtomicLong(0);

        public TransactionLog(String playerId, long initialBalance) {
            this.playerId = playerId;
            this.balance.set(initialBalance);
        }

        /**
         * 记录一笔交易
         */
        public synchronized boolean recordTransaction(long amount, String reason, String txnId) {
            long newBalance = balance.get() + amount;
            
            if (newBalance < 0) {
                log.error("Transaction would result in negative balance: {} + {} = {}", 
                    balance.get(), amount, newBalance);
                return false;
            }

            Transaction txn = new Transaction(
                System.currentTimeMillis(),
                txnId,
                amount,
                newBalance,
                reason
            );
            
            transactions.add(txn);
            balance.set(newBalance);
            
            log.info("Transaction recorded: {} -> {}", playerId, txn);
            return true;
        }

        /**
         * 获取交易历史
         */
        public List<Transaction> getTransactionHistory() {
            return new ArrayList<>(transactions);
        }

        /**
         * 验证金额一致性（用于审计）
         */
        public boolean verifyIntegrity(long expectedBalance) {
            long calculatedBalance = transactions.stream()
                .mapToLong(Transaction::getAmount)
                .sum();
            
            boolean isConsistent = calculatedBalance == expectedBalance;
            if (!isConsistent) {
                log.warn("Balance inconsistency for player {}: calculated={}, expected={}", 
                    playerId, calculatedBalance, expectedBalance);
            }
            return isConsistent;
        }

        public static class Transaction {
            private final long timestamp;
            private final String transactionId;
            private final long amount;
            private final long balanceAfter;
            private final String reason;

            public Transaction(long timestamp, String txnId, long amount, 
                             long balanceAfter, String reason) {
                this.timestamp = timestamp;
                this.transactionId = txnId;
                this.amount = amount;
                this.balanceAfter = balanceAfter;
                this.reason = reason;
            }

            public long getAmount() { return amount; }
            public long getBalanceAfter() { return balanceAfter; }

            @Override
            public String toString() {
                return String.format("Txn[id=%s, amount=%d, balance=%d, reason=%s]",
                    transactionId, amount, balanceAfter, reason);
            }
        }
    }

    /**
     * 分布式锁（用于多个服务器间的同步）
     * 理想情况下使用Redis或ZooKeeper实现
     */
    public static class DistributedLock {
        private final String lockKey;
        private final long lockTimeout;  // 毫秒
        private volatile boolean locked = false;
        private long lockAcquireTime;

        public DistributedLock(String lockKey, long lockTimeout) {
            this.lockKey = lockKey;
            this.lockTimeout = lockTimeout;
        }

        /**
         * 尝试获取锁（非阻塞）
         */
        public synchronized boolean tryLock() {
            if (locked) {
                // 检查是否超时
                if (System.currentTimeMillis() - lockAcquireTime > lockTimeout) {
                    log.warn("Lock timeout for {}, force releasing", lockKey);
                    locked = false;
                } else {
                    return false;
                }
            }

            locked = true;
            lockAcquireTime = System.currentTimeMillis();
            log.debug("Lock acquired: {}", lockKey);
            return true;
        }

        /**
         * 释放锁
         */
        public synchronized void unlock() {
            if (locked) {
                locked = false;
                log.debug("Lock released: {}", lockKey);
            }
        }

        /**
         * 检查锁是否已被获取
         */
        public boolean isLocked() {
            return locked;
        }
    }

    /**
     * 交易隔离级别
     */
    public enum IsolationLevel {
        /** 脏读、不可重复读、幻读都可能发生 */
        READ_UNCOMMITTED,
        
        /** 不会发生脏读 */
        READ_COMMITTED,
        
        /** 可重复读 */
        REPEATABLE_READ,
        
        /** 串行化 */
        SERIALIZABLE
    }
}
