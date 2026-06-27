package com.pepero.jcb.api.thread;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.GameOverReason;
import com.pepero.jcb.api.enums.GameResult;
import com.pepero.jcb.api.event.ChessGameListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ChessGameConcurrencyTest {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Multi-thread concurrency test");

        ChessGame game = new ChessGame();

        AtomicInteger moveMadeCount = new AtomicInteger(0);
        AtomicInteger moveUnmadeCount = new AtomicInteger(0);

        game.addChessGameListener(new ChessGameListener() {
            @Override
            public void onMoveMade(MoveInfo moveInfo) {
                moveMadeCount.incrementAndGet();
            }

            @Override
            public void onMoveUnmade(MoveInfo moveInfo) {
                moveUnmadeCount.incrementAndGet();
            }

            @Override public void onMoveRemade(MoveInfo moveInfo) {}
            @Override public void onPositionJumped(String targetFen) {}
            @Override public void onGameOver(GameResult result, GameOverReason reason) {}
            @Override public void onHistoryChanged() {}
        });

        int readerThreadCount = 10;
        int repeatCount = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(readerThreadCount + 1);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(readerThreadCount + 1);

        Runnable writerTask = () -> {
            try {
                startLatch.await();
                for (int i = 0; i < repeatCount; i++) {
                    game.makeMove("e2e4");
                    Thread.yield(); 
                    game.unmakeMove();
                }
            } catch (Exception e) {
                System.err.println("Write Thread error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        };

        Runnable readerTask = () -> {
            try {
                startLatch.await();
                for (int i = 0; i < repeatCount * 2; i++) {
                    game.getFEN();
                    game.getLegalMoves();
                    game.isCheck();
                    game.getPieceScore();
                }
            } catch (Exception e) {
                System.err.println("Read Thread error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        };

        executor.submit(writerTask);
        for (int i = 0; i < readerThreadCount; i++) {
            executor.submit(readerTask);
        }

        long startTime = System.currentTimeMillis();

        startLatch.countDown();

        boolean finished = endLatch.await(10, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        executor.shutdown();

        System.out.println("=========================================");
        if (!finished) {
            System.err.println("Test failed.");
        } else {
            System.out.println("Test complete.");
            System.out.println("Time: " + (endTime - startTime) + "ms");
            System.out.println("Event:");
            System.out.println("   - Expected make count: " + repeatCount + " | Real make count: " + moveMadeCount.get());
            System.out.println("   - Expected unmake count: " + repeatCount + " | Real unmake count: " + moveUnmadeCount.get());
            
            if (moveMadeCount.get() == repeatCount && moveUnmadeCount.get() == repeatCount) {
                System.out.println("Test complete.");
            } else {
                System.err.println("Expected count doesn't match real");
            }
        }
        System.out.println("=========================================");
    }
}