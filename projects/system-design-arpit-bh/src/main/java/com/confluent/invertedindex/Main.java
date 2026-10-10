package com.confluent.invertedindex;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(10, "Kafka is a streaming platform");
        index.addDocument(3, "Kafka is distributed");
        index.addDocument(7, "Streaming systems are distributed");
        index.addDocument(20, "Kafka is a distributed streaming platform");

        System.out.println(index.searchAnd("kafka", "streaming"));
    }
}

class InvertedIndex {
    private final Map<String, Set<Integer>> index;
    private final Map<String, Map<Integer, List<Integer>>> index2;

    public InvertedIndex() {
        this.index = new HashMap<>();
        this.index2 = new HashMap<>();
    }

    public void addDocument(int docId, String text) {
        String[] words = text.toLowerCase().split("[^a-z]+");

        for(int i=0;i< words.length;i++){
            String word = words[i];
            if(word.isEmpty()) {
                continue;
            }
            Map<Integer, List<Integer>> listMap = index2.getOrDefault(word, new HashMap<>());
            List<Integer> existingList = listMap.getOrDefault(docId, new ArrayList<>());
            existingList.add(i);
            listMap.put(docId, existingList);
            index2.put(word, listMap);
        }
    }

    public void addDocument(int docId, String[] words) {
        for(int i=0;i< words.length;i++) {
            String word = words[i];
            if(word.isEmpty()) {
                continue;
            }
            Map<Integer, List<Integer>> listMap = index2.getOrDefault(word, new HashMap<>());
            List<Integer> positionList = listMap.getOrDefault(docId, new ArrayList<>());
            positionList.add(i);
            listMap.put(docId, positionList);
            index2.put(word, listMap);
        }
    }

    // single word search
    public List<Integer> search(String word) {
        Set<Integer> docs = index.get(word.toLowerCase());

        if(docs == null) {
            return new ArrayList<>();
        }
        List<Integer> result = new ArrayList<>(docs);
        Collections.sort(result);
        return result;
    }

    // double word search
    public List<Integer> searchAnd(String word1, String word2) {
        Set<Integer> set1 = index.get(word1);
        Set<Integer> set2 = index.get(word2);

        if(set1==null || set2==null) {
            return new ArrayList<>();
        }

        Set<Integer> smaller, bigger;
        if(set1.size()<set2.size()) {
            smaller = set1;
            bigger = set2;
        } else {
            smaller = set2;
            bigger = set1;
        }

        List<Integer> result = new ArrayList<>();
        for(int docId: smaller) {
            if(bigger.contains(docId)) {
                result.add(docId);
            }
        }
        Collections.sort(result);
        return result;
    }

    public List<Integer> searchPhrase(List<String> phrase) {
        // first get all docIds that contain all the words
        List<Integer> candidateDocs = getCandidateDocs(phrase);
        //now check each candidate doc,
        // whether the whole phrase contains or not
        List<Integer> ans = new ArrayList<>();
        for(int candidate: candidateDocs) {
            if(isWholePhrasePresent(candidate, phrase)) {
                ans.add(candidate);
            }
        }
        return ans;
    }

    private boolean isWholePhrasePresent(int docId, List<String> phrase) {
        List<Integer> list = index2.get(phrase.get(0)).get(docId);
        for(int p0 : list) {
            boolean matches = true;
            int positionOfFirstWord = p0;
            for(int i=1;i< phrase.size();i++) {
                String word = phrase.get(i);

                List<Integer> phrasePositionsInTheSameDoc = index2.get(word).get(docId);
                if(!phrasePositionsInTheSameDoc.contains(positionOfFirstWord + i)){
                    matches = false;
                    break;
                }
            }
            if(matches) {
                return true;
            }
        }
        return false;
    }

    private List<Integer> getCandidateDocs(List<String> phrase) {
        if(phrase.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Integer> candidates = new HashSet<>(
                index2.getOrDefault(phrase.get(0).toLowerCase(), Collections.emptyMap())
                        .keySet()
        );
        for(int i=1;i< phrase.size();i++) {
            Set<Integer> temp = index2.getOrDefault(phrase.get(i), Collections.emptyMap()).keySet();
            candidates.retainAll(temp);

        }
        List<Integer> result = new ArrayList<>(candidates);
        Collections.sort(result);
        return result;
    }


}

/*
* Claude soln
* import java.util.*;

class DocumentLibrary {
    // term -> (internal doc index -> sorted positions)
    private final Map<String, Map<Integer, int[]>> index = new HashMap<>();
    private final List<String> docIds = new ArrayList<>();

    public DocumentLibrary(List<List<String>> documents) {
        for (List<String> doc : documents) {
            int idx = docIds.size();
            docIds.add(doc.get(0));

            Map<String, List<Integer>> local = new HashMap<>();
            List<String> tokens = tokenize(doc.get(1));
            for (int pos = 0; pos < tokens.size(); pos++) {
                local.computeIfAbsent(tokens.get(pos), k -> new ArrayList<>()).add(pos);
            }
            for (Map.Entry<String, List<Integer>> e : local.entrySet()) {
                int[] arr = e.getValue().stream().mapToInt(Integer::intValue).toArray();
                index.computeIfAbsent(e.getKey(), k -> new HashMap<>()).put(idx, arr);
            }
        }
    }

    public List<String> search(String phrase) {
        List<String> words = tokenize(phrase);
        if (words.isEmpty()) return new ArrayList<>();

        List<Map<Integer, int[]>> postings = new ArrayList<>();
        for (String w : words) {
            Map<Integer, int[]> p = index.get(w);
            if (p == null) return new ArrayList<>();   // a word is missing entirely
            postings.add(p);
        }

        // iterate over the rarest term's documents
        Map<Integer, int[]> rarest = postings.get(0);
        for (Map<Integer, int[]> p : postings) {
            if (p.size() < rarest.size()) rarest = p;
        }

        List<String> result = new ArrayList<>();
        for (int doc : rarest.keySet()) {
            if (containsPhrase(doc, postings)) result.add(docIds.get(doc));
        }
        return result;
    }

    private boolean containsPhrase(int doc, List<Map<Integer, int[]>> postings) {
        int[][] pos = new int[postings.size()][];
        for (int i = 0; i < pos.length; i++) {
            pos[i] = postings.get(i).get(doc);
            if (pos[i] == null) return false;
        }
        for (int p0 : pos[0]) {
            boolean ok = true;
            for (int i = 1; i < pos.length; i++) {
                if (Arrays.binarySearch(pos[i], p0 + i) < 0) { ok = false; break; }
            }
            if (ok) return true;
        }
        return false;
    }

    private static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String w : text.toLowerCase().split("[^a-z0-9]+")) {
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }
}*/
