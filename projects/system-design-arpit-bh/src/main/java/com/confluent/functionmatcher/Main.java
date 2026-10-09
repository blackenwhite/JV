package com.confluent.functionmatcher;

import java.util.*;

public class Main {
}

class Function {
    String name;
    List<String> argumentTypes;
    boolean isVariadic;

    public Function(String name, List<String> argumentTypes, boolean isVariadic) {
        this.name = name;
        this.argumentTypes = argumentTypes;
        this.isVariadic = isVariadic;
    }
}

class FunctionLibrary {
    TrieNode root;

    public FunctionLibrary() {
        root = new TrieNode("0");
    }

    private void insertFunction(Function function) {
        TrieNode cur = root;
        List<String> argumentTypes = function.argumentTypes;

        for(String argType: argumentTypes) {
            if(!cur.children.containsKey(argType)) {
                cur.children.put(argType, new TrieNode(argType));
            }
            cur = cur.children.get(argType);
        }
        if(function.isVariadic) {
            cur.isWildCard = true;
        }
        cur.function = function;

    }

    public void register(Set<Function> functionSet) {
        for(Function f: functionSet) {
            insertFunction(f);
        }
    }

    public List<Function> findMatches(Function f) {
        List<String> argumentTypes = f.argumentTypes;
        int n = argumentTypes.size();
        TrieNode cur = root;

        List<Function> ans = new ArrayList<>();
        for(int i=0;i<n;i++) {
            String argType = argumentTypes.get(i);
            if(cur.isWildCard) {
                // check whether the wildcard rule can actually apply
                boolean isWildcardApplicable = checkWildCardApplicability(argumentTypes, i-1);
                if(isWildcardApplicable && cur.function != null) {
                    ans.add(cur.function);
                }
            }
            if(cur.children.get(argType)==null) {
                return ans;
            }
            cur = cur.children.get(argType);
        }

        // explore all from here: basically all of them down are matches
        dfs(cur, ans);
        return ans;
    }

    private void dfs(TrieNode node, List<Function> ans) {
        if(node.function!=null) {
            ans.add(node.function);
        }

        for(TrieNode trieNode: node.children.values()) {
            dfs(trieNode, ans);
        }
    }

    private boolean checkWildCardApplicability(List<String> args, int start) {
        int j = start;
        while(j<args.size()){
            if(!args.get(start).equals(args.get(j++))) {
                return false;
            }
        }
        return true;
    }
}

class TrieNode{
    String key;
    Map<String, TrieNode> children;
    boolean isWildCard;
    Function function;

    public TrieNode(String key) {
        this.key = key;
        children = new HashMap<>();
        isWildCard = false;
        function = null;
    }
}


