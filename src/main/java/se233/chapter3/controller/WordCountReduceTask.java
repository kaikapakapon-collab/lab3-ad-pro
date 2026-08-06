package se233.chapter3.controller;

import se233.chapter3.model.FileFreq;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class WordCountReduceTask implements Callable<LinkedHashMap<String, List<FileFreq>>> {
    private Map<String, FileFreq>[] wordMap;

    public WordCountReduceTask(Map<String, FileFreq>[] wordMap) {
        this.wordMap = wordMap;
    }

    @Override
    public LinkedHashMap<String, List<FileFreq>> call() throws Exception {
        LinkedHashMap<String, List<FileFreq>> uniqueSets;
        List<Map<String, FileFreq>> wordMapList = new ArrayList<>(Arrays.asList(wordMap));

        uniqueSets = wordMapList.stream()
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.groupingBy(
                        e -> e.getKey(),
                        Collector.of(
                                () -> new ArrayList<FileFreq>(),
                                (list, item) -> list.add(item.getValue()),
                                (current_list, new_items) -> {
                                    current_list.addAll(new_items);
                                    return current_list;
                                })
                ))
                .entrySet()
                .stream()
                // Exercise 3.5 (2): เรียงความถี่ของแต่ละไฟล์ด้านในจากมากไปน้อย
                // จัดเรียงความถี่ภายใน list ของแต่ละคำจากมากไปน้อย
                .peek(e -> e.getValue().sort((f1, f2) -> f2.getFreq().compareTo(f1.getFreq())))
                // Exercise 3.5 (1): เรียงลำดับคำตามความถี่รวม (Total Frequency) จากมากไปน้อย
                .sorted((e1, e2) -> {
                    // หาผลรวมความถี่ของค่าที่ 1
                    int totalFreq1 = e1.getValue().stream().mapToInt(FileFreq::getFreq).sum();
                    // หาผลรวมความถี่ของค่าที่ 2
                    int totalFreq2 = e2.getValue().stream().mapToInt(FileFreq::getFreq).sum();
                    //  เปรียบเทียบผลรวม โดยเอส e2 ขึ้นตามด้วย e1 เพื่อเรียงจากมากไปน้อย
                    return Integer.compare(totalFreq2, totalFreq1);
                })
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue(),
                        (v1, v2) -> v1, () -> new LinkedHashMap<>()));
        return uniqueSets;
    }
}