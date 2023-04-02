package ru.javaops.masterjava.matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static ru.javaops.masterjava.matrix.MainMatrix.*;

/**
 * gkislin
 * 03.07.2016
 */
public class MatrixUtil {

    // T_O_D_O implement parallel multiplication matrixA*matrixB
    public static int[][] concurrentMultiply(int[][] matrixA, int[][] matrixB, ExecutorService executor) {
        int[][] matrixC = new int[MATRIX_SIZE][MATRIX_SIZE];
        for (int rowA = 0; rowA < MATRIX_SIZE; rowA += TASK_SIZE) {
            executor.submit(new Task(matrixA, matrixB, matrixC, rowA));
        }
        return matrixC;
    }

    public static int[][] concurrentMultiplyCB(int[][] matrixA, int[][] matrixB, CyclicBarrier barrier) {
        final int[][] matrixC = new int[MATRIX_SIZE][MATRIX_SIZE];
        List<Thread> threads = new ArrayList<>(THREAD_NUMBER);
        for (int rowA = 0; rowA < MATRIX_SIZE; rowA++) {
            threads.add(new TaskCB(matrixA, matrixB, matrixC, rowA, barrier));
            if (threads.size() == THREAD_NUMBER) {
                for (Thread thread : threads) {
                    thread.start();
                }
                for (Thread thread : threads) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                threads.clear();
            }
        }
        return matrixC;
    }

    public static int[][] concurrentMultiplyFJP(ForkJoinPool fjp) {
        final int[][] matrixC = new int[MATRIX_SIZE][MATRIX_SIZE];
        TaskFJP task = new TaskFJP(matrixC, 0, MATRIX_SIZE);
        fjp.<Void>invoke(task);
        return matrixC;
    }

    // T_O_D_O optimize by https://habrahabr.ru/post/114797/
    public static int[][] singleThreadMultiply(int[][] matrixA, int[][] matrixB) {
        final int matrixSize = matrixA.length;
        final int[][] matrixC = new int[matrixSize][matrixSize];

        int[] columnMatrixB = new int[MainMatrix.MATRIX_SIZE];
        try {
            for (int i = 0; ; i++) {
                for (int k = 0; k < MainMatrix.MATRIX_SIZE; k++) {
                    columnMatrixB[k] = matrixB[k][i];
                }
                for (int j = 0; j < MainMatrix.MATRIX_SIZE; j++) {
                    int[] rowMatrixA = matrixA[j];
                    int sum = 0;
                    for (int k = 0; k < MainMatrix.MATRIX_SIZE; k++) {
                        sum += rowMatrixA[k] * columnMatrixB[k];
                    }
                    matrixC[j][i] = sum;
                }
            }
        } catch (IndexOutOfBoundsException ignored) {
        }
        return matrixC;
    }

    public static int[][] create(int size) {
        int[][] matrix = new int[size][size];
        Random rn = new Random();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrix[i][j] = rn.nextInt(6);
            }
        }
        return matrix;
    }

    public static boolean compare(int[][] matrixA, int[][] matrixB) {
        final int matrixSize = matrixA.length;
        for (int i = 0; i < matrixSize; i++) {
            for (int j = 0; j < matrixSize; j++) {
                if (matrixA[i][j] != matrixB[i][j]) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int[][] transpone(int[][] matrix) {
        int[][] matrixTransponed = new int[MATRIX_SIZE][MATRIX_SIZE];
        for (int i = 0; i < MATRIX_SIZE; i++) {
            for (int j = 0; j < MATRIX_SIZE; j++) {
                matrixTransponed[j][i] = matrix[i][j];
            }
        }
        return matrixTransponed;
    }

    private static void calculate(int[][] matrixA, int[][] matrixB, int[][] matrixC, int rowA) {
        int[] columnMatrixB = new int[MATRIX_SIZE];
        for (int i = rowA; i < rowA + TASK_SIZE; ++i) {
            calcIJ(matrixA, matrixB, matrixC, columnMatrixB, i);
        }
    }

    private static void calculate(int[][] matrixA, int[][] matrixB, int[][] matrixC, int rowFrom, int rowTo) {
        int[] columnMatrixB = new int[MATRIX_SIZE];
        for (int row = rowFrom; row < rowTo; row++) {
            calcIJ(matrixA, matrixB, matrixC, columnMatrixB, row);
        }
    }

    static void calcIJ(int[][] matrixA, int[][] matrixB, int[][] matrixC, int[] columnMatrixB, int column) {
        for (int k = 0; k < MATRIX_SIZE; k++) {
            columnMatrixB[k] = matrixB[k][column];
        }
        for (int j = 0; j < MATRIX_SIZE; j++) {
            int[] rowMatrixA = matrixA[j];
            int sum = 0;
            for (int k = 0; k < MATRIX_SIZE; k++) {
                sum += rowMatrixA[k] * columnMatrixB[k];
            }
            matrixC[j][column] = sum;
        }
    }

    private static void calcIJ1(int[][] matrixA, int[][] matrixB, int[][] matrixC, int column) {
        int[] columnMatrixB = new int[MATRIX_SIZE];
        for (int k = 0; k < MATRIX_SIZE; k++) {
            columnMatrixB[k] = matrixB[k][column];
        }
        for (int j = 0; j < MATRIX_SIZE; j++) {
            int[] rowMatrixA = matrixA[j];
            int sum = 0;
            for (int k = 0; k < MATRIX_SIZE; k++) {
                sum += rowMatrixA[k] * columnMatrixB[k];
            }
            matrixC[j][column] = sum;
        }
    }

    private static void calculateCB(int[][] matrixA, int[][] matrixB, int[][] matrixC, int rowA) {
        int[] columnMatrixB = new int[MATRIX_SIZE];
        calcIJ(matrixA, matrixB, matrixC, columnMatrixB, rowA);
    }

    public static int[][] concurrentMultiplyParallelStream(final int[][] matrixA, final int[][] matrixB, IntStream stream) {
        final int[][] matrixC = new int[MATRIX_SIZE][MATRIX_SIZE];
        stream.forEach(row -> {
            calculate(matrixA, matrixB, matrixC, row);
        });
        return matrixC;
    }

    //concurrentMatrixParralelStream time, msec: 17 943,034
    public static int[][] concurrentMultiplyParallelStream1(final int[][] matrixA, final int[][] matrixB) {
        return Arrays.stream(matrixA)
                .parallel()
                .map(AMatrixRow -> IntStream.range(0, matrixB[0].length)
                        .map(i -> IntStream.range(0, matrixB.length)
                                .map(j -> AMatrixRow[j] * matrixB[j][i])
                                .sum()
                        )
                        .toArray())
                .toArray(int[][]::new);
    }

    static class Task implements Runnable {
        private final int[][] matrixA;
        private final int[][] matrixB;
        private final int[][] matrixC;

        private final int rowA;

        public Task(int[][] matrixA, int[][] matrixB, int[][] matrixC, int rowA) {
            this.matrixA = matrixA;
            this.matrixB = matrixB;
            this.matrixC = matrixC;
            this.rowA = rowA;
        }

        @Override
        public void run() {
            calculate(matrixA, matrixB, matrixC, rowA);
        }

    }

    static class TaskCB extends Thread {
        private final int[][] matrixA;
        private final int[][] matrixB;
        private final int[][] matrixC;
        private final int rowA;

        private final CyclicBarrier barrier;

        public TaskCB(int[][] matrixA, int[][] matrixB, int[][] matrixC, int rowA, CyclicBarrier barrier) {
            this.matrixA = matrixA;
            this.matrixB = matrixB;
            this.matrixC = matrixC;
            this.rowA = rowA;
            this.barrier = barrier;
        }

        @Override
        public void run() {
            try {
                barrier.await();
                calculateCB(matrixA, matrixB, matrixC, rowA);
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        }
    }

    static class TaskFJP extends RecursiveAction {
        private final int[][] matrixA = MainMatrix.matrixA;
        private final int[][] matrixB = MainMatrix.matrixB;
        private final int[][] matrixC;

        final int lo, hi;

        TaskFJP(int[][] matrixC, int lo, int hi) {
            this.matrixC = matrixC;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            if (hi - lo <= TASK_SIZE / THRESH) {
                for (int i = lo; i < hi; i++) {
                    calculate(matrixA, matrixB, matrixC, i, hi);
                }
            } else {
                int mid = (lo + hi) >>> 1;
                invokeAll(new TaskFJP(matrixC, lo, mid),
                        new TaskFJP(matrixC, mid, hi));
            }
        }
    }
}
