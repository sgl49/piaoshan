package com.hmdp.utils.bloom;

/**
 * 布隆过滤器策略接口
 * 定义布隆过滤器的基本操作
 */
public interface BloomFilterStrategy<T> {
    
    /**
     * 获取策略名称
     * @return 策略名称
     */
    String getName();
    
    /**
     * 初始化布隆过滤器
     * @param expectedInsertions 预计插入元素数量
     * @param falseProbability 误判率
     */
    void init(long expectedInsertions, double falseProbability);
    
    /**
     * 添加元素到布隆过滤器
     * @param value 要添加的元素
     * @return 是否添加成功
     */
    boolean add(T value);
    
    /**
     * 批量添加元素到布隆过滤器
     * @param values 要添加的元素集合
     */
    void addAll(Iterable<T> values);
    
    /**
     * 判断元素是否可能存在于布隆过滤器中
     * @param value 要检查的元素
     * @return 如果可能存在返回true，一定不存在返回false
     */
    boolean contains(T value);
    
    /**
     * 获取当前大小
     * @return 当前大小
     */
    long count();
    
    /**
     * 清空布隆过滤器
     */
    void clear();
} 