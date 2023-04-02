package ru.javaops.masterjava.matrix;

import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * gkislin
 * 03.07.2016
 */
public class MainMatrix {
    public static final int MATRIX_SIZE = 1000;
    static final int TASK_SIZE = 100;
    public static final int THREAD_NUMBER = 10;
    public static final int THRESH = 64;
    static int[][] matrixA;
    static int[][] matrixB;

    static {
        matrixA = MatrixUtil.create(MATRIX_SIZE);
        matrixB = MatrixUtil.create(MATRIX_SIZE);
    }

    public static void main(String[] args) {
        double singleThreadSum = 0.;
        double concurrentThreadSum = 0.;
        double concurrentThreadSumCB = 0.;
        double concurrentThreadSumFJP = 0.;
        double concurrentSumParallelStream = 0.;
        int count = 1;
        while (count < 6) {
            System.out.println("Pass " + count);

            long start = System.nanoTime();
            final int[][] matrixC = MatrixUtil.singleThreadMultiply(matrixA, matrixB);
            double duration = (System.nanoTime() - start) / 1000000.;
            out("Single thread time, msec:       %,.3f", duration);
            singleThreadSum += duration;

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUMBER);
//            ExecutorService executor = Executors.newWorkStealingPool(THREAD_NUMBER);
//            ExecutorService executor = Executors.newCachedThreadPool();
            start = System.nanoTime();
            final int[][] concurrentMatrixC = MatrixUtil.concurrentMultiply(matrixA, matrixB, executor);
            duration = (System.nanoTime() - start) / 1000000.;
            out("Concurrent thread time, msec:    %,.3f", duration);
            concurrentThreadSum += duration;
            executor.shutdown();
            while (!executor.isTerminated()) ;

            if (MatrixUtil.compare(matrixC, concurrentMatrixC)) {
                System.err.println("Comparison (executor) failed");
                break;
            }

            CyclicBarrier barrier = new CyclicBarrier(THREAD_NUMBER);
            start = System.nanoTime();
            final int[][] concurrentMatrixCCB = MatrixUtil.concurrentMultiplyCB(matrixA, matrixB, barrier);
            duration = (System.nanoTime() - start) / 1000000.;
            out("Concurrent CB thread time, msec: %,.3f", duration);
            concurrentThreadSumCB += duration;
            barrier.reset();

            if (MatrixUtil.compare(matrixC, concurrentMatrixCCB)) {
                System.err.println("Comparison (CyclicBarrier) failed");
                break;
            }

            ForkJoinPool fjp = new ForkJoinPool(THREAD_NUMBER);
            start = System.nanoTime();
            final int[][] concurrentMatrixFJP = MatrixUtil.concurrentMultiplyFJP(fjp);
            duration = (System.nanoTime() - start) / 1000000.;
            out("Concurrent ForkJoinPool thread time, msec: %,.3f", duration);
            concurrentThreadSumFJP += duration;
            fjp.shutdown();
            while (!fjp.isTerminated()) ;

            if (MatrixUtil.compare(matrixC, concurrentMatrixFJP)) {
                System.err.println("Comparison (ForkJoinPool) failed");
                break;
            }

            IntStream stream = IntStream.rangeClosed(0, MATRIX_SIZE - 1).parallel().filter(n -> n % TASK_SIZE == 0);
            start = System.nanoTime();
            final int[][] concurrentMatrixParallelStream = MatrixUtil.concurrentMultiplyParallelStream(matrixA, matrixB, stream);
            duration = (System.nanoTime() - start) / 1000000.;
            out("Concurrent concurrentMatrixParralelStream time, msec: %,.3f", duration);
            concurrentSumParallelStream += duration;

            if (MatrixUtil.compare(matrixC, concurrentMatrixParallelStream)) {
                System.err.println("Comparison (ParallelStream) failed");
                break;
            }

            count++;
        }
        out("\nAverage single thread time, msec:               %,9.3f", singleThreadSum / 5.);
        out("Average concurrent thread (executor) time, msec:  %,9.3f", concurrentThreadSum / 5.);
        out("Average concurrent thread (CB) time, msec:        %,9.3f", concurrentThreadSumCB / 5.);
        out("Average concurrent thread (ForkJoinPool) time, msec:        %,9.3f", concurrentThreadSumFJP / 5.);
        out("Average concurrent thread (ParallelStream) time, msec:        %,9.3f", concurrentSumParallelStream / 5.);
    }

    public static void out(String format, double ms) {
        System.out.printf((format) + "%n", ms);
    }
}
