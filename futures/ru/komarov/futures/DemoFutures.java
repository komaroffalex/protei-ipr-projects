package ru.komarov.futures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DemoFutures {
    public static void main(String[] args) {
        CustomExecutorServiceImpl executorService = new CustomExecutorServiceImpl(5, 100L, 50L);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 1; i < 11; i++) {
            futures.add(executorService.submit(compute(i, 10000L)));
        }
        try {
            for (int i = 0; i < 10; i++) {
                Integer result = futures.get(i).get();
                System.out.println("Successfully computed future with result: " + result);
            }
            executorService.shutdown();
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Failed to compute future due to: " + e.getMessage());
        }
    }

    private static Callable<Integer> compute(Integer returnValue, long waitMillis) {
        return () -> {
            try {
                Thread.sleep(waitMillis);
                return returnValue;
            } catch (InterruptedException e) {
                System.out.println("Failed to compute due to: " + e.getMessage());
            }
            return null;
        };
    }

    private static Callable<String> compute(String returnValue, long waitMillis) {
        return () -> {
            try {
                Thread.sleep(waitMillis);
                return returnValue;
            } catch (InterruptedException e) {
                System.out.println("Failed to compute due to: " + e.getMessage());
            }
            return null;
        };
    }
}
