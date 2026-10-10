package com.practice.invertedindex;

import java.util.*;

public class PhraseSearchEngine {
    // key: word, List<Integer> := docId
    // cloud computing

    // doc id: 1
    // cloud : {0, 14, 34}
    // computing: {1, 16, 40}
    // key: word, value: {{docId: positions[]}}

    // N *M
    // phrase search
    //


    public PhraseSearchEngine() {
        this.index = new HashMap<>();
    }

    final Map<String, Map<Integer, Set<Integer>>> index;

    public void preprocess(List<List<String>> docs) {
        int len = docs.size();
        for(int docId = 0;docId<len;docId++) {
            List<String> doc = docs.get(docId);
            int n = doc.size();
            for(int pos=0;pos<n;pos++) {
                String word = doc.get(pos);
                if(word.isEmpty()) {
                    continue;
                }
                Map<Integer, Set<Integer>> posMap = index.getOrDefault(word, new HashMap<>());
                Set<Integer> positions = posMap.getOrDefault(docId, new HashSet<>());
                positions.add(pos);
                posMap.put(docId, positions);
                index.put(word, posMap);
            }
        }
    }

    // search for a word
    public List<Integer> getDocIds(String word) {
        if(index.containsKey(word)) {
            return new ArrayList<>(index.get(word).keySet());
        }else{
            return Collections.emptyList();
        }

    }

    // search for a phrase
    public List<Integer> searchPhrase(List<String> phrase) {
        // get candidate docIds
        if(phrase.isEmpty()) {
            return Collections.emptyList();
        }
        int n = phrase.size();
        Set<Integer> candidates = index.get(phrase.getFirst()).keySet(); // O(1) , O(K) to get candidates , K = number of words in phrase
        for(int i=1;i<n;i++) {
            Set<Integer> temp = index.get(phrase.get(i)).keySet();
            candidates.retainAll(temp);
        }

        // for each docId check whether entire phrase is present
        Set<Integer> ans = new HashSet<>();
        for(int docId: candidates) {
            Set<Integer> positionOfFirstWord = index.get(phrase.getFirst()).get(docId); // pos0 := postions of first word O(1)*K*N
            boolean found = false;
            for(int p: positionOfFirstWord) {
                if(wholePhrasePresent(phrase, p, docId)) {
                    ans.add(docId);
                    break;
                }
            }

        }
        return new ArrayList<>(ans);
    }

    private boolean wholePhrasePresent(List<String> phrase, int posFirstWord, int docId) {
        boolean matches = true;
        for(int i=1;i<phrase.size();i++) {
            // check whether this word is present in that docId, in (pos0+i) th position
            Set<Integer> positions = index.get(phrase.get(i)).get(docId);
            if(!positions.contains(posFirstWord+i)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        PhraseSearchEngine ps = new PhraseSearchEngine();
        String[][] docs = {{"cloud", "computing" }, {"cloud", "cloud", "computing"}, {"computing", "cloud"}, {"computing1", "cloud1"}, {"The" , "world", "has", "cloud", "computing"}};
        List<List<String>> documents = new ArrayList<>();
        for(int i=0;i<docs.length;i++){
            List<String> doc = new ArrayList<>();
            for(int j=0;j<docs[i].length;j++){
                doc.add(docs[i][j]);
            }
            documents.add(doc);
        }
        ps.preprocess(documents);

        List<Integer> ids = ps.getDocIds("cloud");
//        for(int id: ids) {
//            System.out.println(id);
//        }

        List<String> phrase = Arrays.asList("cloud", "computing");
        ids = ps.searchPhrase(phrase);
        for(int id: ids) {
            System.out.println(id);
        }
    }
}


