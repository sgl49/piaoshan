import java.util.*;

class Solution {
    private int[] gems;
    private int mod;
    private int target;
    private Map<String, Integer> memo;
    
    public int treeOfInfiniteSouls(int[] gem, int p, int target) {
        this.gems = gem;
        this.mod = p;
        this.target = target;
        this.memo = new HashMap<>();
        
        return construct(0, gem.length - 1);
    }
    
    // 构建使用gems[l...r]的二叉树，返回满足条件的方案数
    private int construct(int l, int r) {
        if (l > r) return 0;
        
        String key = l + "," + r;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        
        // 只有一个节点的情况
        if (l == r) {
            // 模拟遍历过程: 1(进入) -> gems[l](叶子节点值) -> 9(离开)
            int num = computeNumber("1" + gems[l] + "9");
            int result = (num == target) ? 1 : 0;
            memo.put(key, result);
            return result;
        }
        
        int ans = 0;
        
        // 枚举根节点位置
        for (int root = l; root <= r; root++) {
            // 如果选root作为根节点，计算左右子树的方案数
            List<String> leftTraversals = new ArrayList<>();
            List<String> rightTraversals = new ArrayList<>();
            
            // 生成左子树所有可能的遍历序列
            generateTraversals(l, root-1, leftTraversals);
            
            // 生成右子树所有可能的遍历序列
            generateTraversals(root+1, r, rightTraversals);
            
            // 如果左子树为空，添加空序列
            if (leftTraversals.isEmpty()) {
                leftTraversals.add("");
            }
            
            // 如果右子树为空，添加空序列
            if (rightTraversals.isEmpty()) {
                rightTraversals.add("");
            }
            
            // 组合左右子树遍历序列
            for (String leftT : leftTraversals) {
                for (String rightT : rightTraversals) {
                    // 构建完整的遍历序列
                    String fullTraversal = simulate(root, leftT, rightT);
                    
                    // 计算序列对应的数字对mod取余的结果
                    int num = computeNumber(fullTraversal);
                    
                    // 如果满足条件，增加方案数
                    if (num == target) {
                        ans = (ans + 1) % mod;
                    }
                }
            }
        }
        
        memo.put(key, ans);
        return ans;
    }
    
    // 生成从gems[l...r]构建的二叉树的所有可能遍历序列
    private void generateTraversals(int l, int r, List<String> traversals) {
        if (l > r) return;
        
        // 如果只有一个节点
        if (l == r) {
            traversals.add("1" + gems[l] + "9");
            return;
        }
        
        // 枚举根节点位置
        for (int root = l; root <= r; root++) {
            List<String> leftTraversals = new ArrayList<>();
            List<String> rightTraversals = new ArrayList<>();
            
            // 生成左右子树的遍历序列
            generateTraversals(l, root-1, leftTraversals);
            generateTraversals(root+1, r, rightTraversals);
            
            // 如果左子树为空，添加空序列
            if (leftTraversals.isEmpty()) {
                leftTraversals.add("");
            }
            
            // 如果右子树为空，添加空序列
            if (rightTraversals.isEmpty()) {
                rightTraversals.add("");
            }
            
            // 组合左右子树遍历序列
            for (String leftT : leftTraversals) {
                for (String rightT : rightTraversals) {
                    traversals.add(simulate(root, leftT, rightT));
                }
            }
        }
    }
    
    // 模拟遍历过程，构建完整的遍历序列
    private String simulate(int rootIdx, String leftTraversal, String rightTraversal) {
        StringBuilder sb = new StringBuilder();
        sb.append("1"); // 进入根节点
        
        if (leftTraversal.isEmpty() && rightTraversal.isEmpty()) {
            // 如果是叶子节点，记录宝石值
            sb.append(gems[rootIdx]);
        } else {
            // 优先遍历左子树
            if (!leftTraversal.isEmpty()) {
                sb.append(leftTraversal);
            }
            
            // 再遍历右子树
            if (!rightTraversal.isEmpty()) {
                sb.append(rightTraversal);
            }
        }
        
        sb.append("9"); // 离开节点
        return sb.toString();
    }
    
    // 计算数字序列mod p的结果
    private int computeNumber(String s) {
        int result = 0;
        for (char c : s.toCharArray()) {
            result = (result * 10 + (c - '0')) % mod;
        }
        return result;
    }
} 