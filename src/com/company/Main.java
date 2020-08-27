package com.company;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    public static Map<Integer, String> actions = new LinkedHashMap<>();
    public static Map<String, Set<Integer>> invertedIndex = new HashMap<>();

    static {
        actions.put(1, "Find a person");
        actions.put(2, "Print all people");
        actions.put(0, "Exit");
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        String dataFilePath = getOption("--data", args);
        List<String> people = getPeople(dataFilePath, scanner);
        while (true) {
            int menuAction = getMenuAction(scanner, actions);
            switch (menuAction) {
                case 0:
                    System.out.println("\nBye!");
                    return;
                case 1:
                    findPersonAction(scanner, people);
                    break;
                case 2:
                    printPeople(people);
            }
        }
    }

    private static String getOption(String option, String[] args) {
        boolean optionFound = false;
        for (String arg : args) {
            if (optionFound) {
                return arg;
            }
            if (arg.equals(option)) {
                optionFound = true;
            }
        }
        return null;
    }

    private static void findPersonAction(Scanner scanner, List<String> people) {
        System.out.println(Arrays.stream(SearchStrategyType.values())
                               .map(Enum::name)
                               .collect(Collectors.joining(", ", "\nSelect a matching strategy: ", "")));
        SearchStrategyType searchStrategyType = SearchStrategyType.valueOf(scanner.nextLine().trim());
        final Finder finder;
        switch (searchStrategyType) {
            case ALL:
                finder = new Finder(new AllWordMatchSearchStrategy());
                break;
            case ANY:
                finder = new Finder(new AnyWordMatchSearchStrategy());
                break;
            case NONE:
                finder = new Finder(new NoneWordMatchSearchStrategy());
                break;
            default:
                throw new IllegalStateException("Undefined strategy " + searchStrategyType.name());
        }
        System.out.println("\nEnter a name or email to search all suitable people.");
        String query = scanner.nextLine().trim();
        List<String> foundPeople = finder.find(query, people);
        if (foundPeople.isEmpty()) {
            System.out.println("No matching people found.");
        } else {
            foundPeople.forEach(System.out::println);
        }
    }

    private static void printPeople(List<String> people) {
        System.out.println("\n=== List of people ===");
        people.forEach(System.out::println);
    }

    private static int getMenuAction(Scanner scanner, Map<Integer, String> actions) {
        int action;
        boolean repeat;
        do {
            System.out.println("\n=== Menu ===");
            actions.forEach(
                (key, value) -> System.out.println(key + ". " + value)
            );
            action = Integer.parseInt(scanner.nextLine());
            repeat = !actions.containsKey(action);
            if (repeat) {
                System.out.println("\nIncorrect option! Try again.");
            }
        }
        while (repeat);
        return action;
    }

    private static List<String> getPeople(String filePath, Scanner defaultScanner) {
        if (filePath == null || filePath.isEmpty()) {
            System.out.println("Enter the number of people:");
            int peopleCount = Integer.parseInt(defaultScanner.nextLine().trim());
            System.out.println("Enter all people:");
            return IntStream.range(0, peopleCount)
                .mapToObj(i -> addLineToInvertedIndex(i, defaultScanner.nextLine().trim()))
                .collect(Collectors.toList());
        }
        try (Scanner scanner = new Scanner(new File(filePath))) {
            List<String> people = new ArrayList<>();
            for (int i = 0; scanner.hasNextLine(); i++) {
                people.add(addLineToInvertedIndex(i, scanner.nextLine()));
            }
            return people;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private static String addLineToInvertedIndex(Integer index, String line) {
        Arrays.stream(line.toLowerCase().split("\\s+"))
            .forEach(word -> invertedIndex.merge(
                word,
                Set.of(index),
                (oldValue, newValue) -> {
                    Set<Integer> value = oldValue instanceof LinkedHashSet ?
                        oldValue : new LinkedHashSet<>(oldValue);
                    value.addAll(newValue);
                    return value;
                }));
        return line;
    }

    enum SearchStrategyType {
        ALL,
        ANY,
        NONE
    }

    interface SearchStrategy {
        Set<Integer> getIndexes(String query, List<String> people);
    }

    static class Finder {
        private final SearchStrategy strategy;

        Finder(SearchStrategy strategy) {
            this.strategy = strategy;
        }

        public List<String> find(String query, List<String> people) {
            return strategy.getIndexes(query, people).stream()
                .sorted()
                .map(people::get)
                .collect(Collectors.toList());
        }
    }

    static class AnyWordMatchSearchStrategy implements SearchStrategy {

        @Override
        public Set<Integer> getIndexes(String query, List<String> people) {
            return Arrays.stream(query.toLowerCase().split("\\s+"))
                .filter(invertedIndex::containsKey)
                .map(queryWord -> invertedIndex.get(queryWord))
                .reduce(new HashSet<>(), (a, b) -> {
                    a.addAll(b);
                    return a;
                });
        }
    }

    static class NoneWordMatchSearchStrategy implements SearchStrategy {
        private static AnyWordMatchSearchStrategy anyWordMatchSearchStrategy = new AnyWordMatchSearchStrategy();

        @Override
        public Set<Integer> getIndexes(String query, List<String> people) {
            Set<Integer> anyMatchIndexes = anyWordMatchSearchStrategy.getIndexes(query, people);
            return IntStream.range(0, people.size())
                .boxed()
                .filter(Predicate.not(anyMatchIndexes::contains))
                .collect(Collectors.toSet());
        }
    }

    static class AllWordMatchSearchStrategy implements SearchStrategy {

        @Override
        public Set<Integer> getIndexes(String query, List<String> people) {
            return Arrays.stream(query.toLowerCase().split("\\s+"))
                .map(queryWord -> invertedIndex.getOrDefault(queryWord, Set.of()))
                .reduce((a, b) -> {
                    if (a.isEmpty() || b.isEmpty()) {
                        return Set.of();
                    }
                    Set<Integer> result = new HashSet<>(a);
                    result.retainAll(b);
                    return result;
                })
                .orElse(Set.of());
        }
    }

}

