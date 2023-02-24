package ru.javaops.masterjava.matrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static ru.javaops.masterjava.matrix.MainMatrix.MATRIX_SIZE;
import static ru.javaops.masterjava.matrix.MainMatrix.NUMBER_OF_TASKS;

/**
 * gkislin
 * 03.07.2016
 */
public class MatrixUtil {

    // T_O_D_O implement parallel multiplication matrixA*matrixB
    public static int[][] concurrentMultiply(int[][] matrixA, int[][] matrixB, ExecutorService executor) throws InterruptedException, ExecutionException {
        final int matrixSize = matrixA.length;
        final int[][] matrixC = new int[matrixSize][matrixSize];
/*
        List<CallableTask> tasks = new ArrayList<>();
        tasks.add(new Task(matrixA, matrixB, matrixC, i + n, j));
        executor.invokeAll(tasks);
*/
        for (int i = 0; i < MATRIX_SIZE; i += NUMBER_OF_TASKS) {
            for (int j = 0; j < MATRIX_SIZE; j++) {
                for (int n = 0; i + n < MATRIX_SIZE && n < NUMBER_OF_TASKS; n++) {
                    executor.submit(new Task(matrixA, matrixB, matrixC, i + n, j));
                }
            }

        }
        while (((ThreadPoolExecutor) executor).getQueue().size() > 0) ;
        return matrixC;
    }

    // TODO optimize by https://habrahabr.ru/post/114797/
    public static int[][] singleThreadMultiply(int[][] matrixA, int[][] matrixB) {
        final int matrixSize = matrixA.length;
        final int[][] matrixC = new int[matrixSize][matrixSize];
        final int[][] matrixBTransponed = transpone(matrixB);

        int[] columnMatrixB = new int[MATRIX_SIZE];
        try {
            for (int i = 0; ; i++) {
                for (int k = 0; k < MATRIX_SIZE; k++) {
                    columnMatrixB[k] = matrixB[k][i];
                }
                for (int j = 0; j < MATRIX_SIZE; j++) {
                    int[] rowMatrixA = matrixA[j];
                    int sum = 0;
                    for (int k = 0; k < MATRIX_SIZE; k++) {
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
                matrix[i][j] = rn.nextInt(10);
            }
        }
        return matrix;
    }

    public static boolean compare(int[][] matrixA, int[][] matrixB) {
        final int matrixSize = matrixA.length;
        for (int i = 0; i < matrixSize; i++) {
            for (int j = 0; j < matrixSize; j++) {
                if (matrixA[i][j] != matrixB[i][j]) {
                    System.out.printf("matrixA[%d][%d] = %d != matrixB[%d][%d] = %d", i, j, matrixA[i][j], i, j, matrixB[i][j]);
                    return false;
                }
            }
        }
        return true;
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

    static class Task implements Runnable {
        private final int[][] matrixA;
        private final int[][] matrixB;
        private final int[][] matrixC;
        private final int i;
        private final int j;

        public Task(int[][] matrixA, int[][] matrixB, int[][] matrixC, int i, int j) {
            this.matrixA = matrixA;
            this.matrixB = matrixB;
            this.matrixC = matrixC;
            this.i = i;
            this.j = j;
        }

        @Override
        public void run() {
//            System.out.printf("[%d][%d] Thread %20s started : %d%n", i, j, Thread.currentThread().getName(), System.nanoTime());
            int sum = 0;
            for (int k = 0; k < matrixA.length; k++) {
                sum += matrixA[i][k] * matrixB[k][j];
            }
            matrixC[i][j] = sum;
//            System.out.printf("[%d][%d] Thread %20s finished: %d%n", i, j, Thread.currentThread().getName(), System.nanoTime());
        }
    }
}
