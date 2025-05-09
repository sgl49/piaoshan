package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 添加博客点赞数量统计的key前缀
    private static final String BLOG_LIKED_COUNT = "blog:liked:count:";

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        String countKey = BLOG_LIKED_COUNT + id;

        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 未点赞，可以点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 保存点赞用户，设置过期时间
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
                stringRedisTemplate.expire(key, 24, TimeUnit.HOURS);

                // 更新点赞统计，设置过期时间
                stringRedisTemplate.opsForZSet().add(countKey, id.toString(),
                        getById(id).getLiked());  // 使用数据库中的点赞数
                stringRedisTemplate.expire(countKey, 24, TimeUnit.HOURS);
            }
        } else {
            // 已点赞，取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
                // 更新点赞统计
                Blog blog = getById(id);
                stringRedisTemplate.opsForZSet().add(countKey, id.toString(),
                        blog.getLiked());  // 使用更新后的数据库点赞数
            }
        }

        // 更新点赞排行榜
        String rankKey = "blog:liked:rank";
        if (score == null) {
            // 点赞，排行榜分数+1
            stringRedisTemplate.opsForZSet().incrementScore(rankKey, id.toString(), 1);
        } else {
            // 取消点赞，排行榜分数-1
            stringRedisTemplate.opsForZSet().incrementScore(rankKey, id.toString(), -1);
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikesById(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        //查询top5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 6);
        if (top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析出用户id
        List<Long> userIds = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String join = StrUtil.join(",", userIds);
        //根据id查询用户
        List<UserDTO> userDTOS = userService.lambdaQuery()
                .in(User::getId,userIds)
                .last("order by field(id,"+join+")")
                .list()
                .stream().map(user ->
                        BeanUtil.copyProperties(user, UserDTO.class)
                ).collect(Collectors.toList());
        //返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("新增笔记失败");
        }
        //查询笔记作者的所有粉丝
        List<Follow> follows = followService.lambdaQuery()
                .eq(Follow::getFollowUserId, user.getId())
                .list();
        //推送笔记给所有粉丝
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            //推送
            String key="feed:"+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        //查询收件箱
        String key="feed:"+user.getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if (typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析数据 blogId minTime offset
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime =0;
        int os=1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取id
            String blogId = typedTuple.getValue();
            ids.add(Long.valueOf(blogId));
            long time = typedTuple.getScore().longValue();
            if (time==minTime){
                os++;
            }else {
                minTime = time;
                os=1;
            }
        }
        //根据 查询blog
        List<Blog> blogs=new ArrayList<>(ids.size());
        for (Long id : ids) {
            Blog blog = getById(id);
            blogs.add(blog);
        }
        blogs.forEach(this::isBlogLiked);
        //封装 返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    /**
     * 查询点赞数量前5的博客 - 使用预热的点赞排行榜数据
     */
    @Override
    public Result queryBlogByLikedTop5() {
        // 1. 从Redis缓存中获取点赞排行榜
        String rankKey = "blog:liked:rank";
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(rankKey, 0, 4);

        // 2. 判断缓存是否存在
        if (typedTuples == null || typedTuples.isEmpty()) {
            log.info("点赞排行榜缓存未命中，查询数据库并临时构建排行榜");
            // 缓存未命中，查询数据库
            List<Blog> blogs = query()
                    .orderByDesc("liked")
                    .last("LIMIT 5")
                    .list();

            if (blogs.isEmpty()) {
                return Result.ok(Collections.emptyList());
            }

            // 临时构建排行榜
            for (Blog blog : blogs) {
                stringRedisTemplate.opsForZSet().add(rankKey, blog.getId().toString(), blog.getLiked());
            }
            // 设置缓存过期时间
            stringRedisTemplate.expire(rankKey, 30, TimeUnit.MINUTES);

            // 查询博客详细信息
            blogs.forEach(blog -> {
                queryBlogUser(blog);
                isBlogLiked(blog);
            });

            log.info("临时构建点赞排行榜完成，共{}条记录", blogs.size());
            return Result.ok(blogs);
        }

        log.info("命中点赞排行榜缓存，开始查询博客详情");
        // 3. 缓存命中，解析出博客ID和点赞数
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        Map<Long, Integer> scoreMap = new HashMap<>();

        typedTuples.forEach(tuple -> {
            Long blogId = Long.valueOf(tuple.getValue());
            blogIds.add(blogId);
            scoreMap.put(blogId, tuple.getScore().intValue());
        });

        // 4. 先尝试从缓存查询博客详情
        List<Blog> blogs = new ArrayList<>(blogIds.size());
        List<Long> missingIds = new ArrayList<>();

        for (Long blogId : blogIds) {
            // 尝试从缓存获取
            String blogKey = RedisConstants.BLOG_LIKED_KEY + blogId;
            String json = stringRedisTemplate.opsForValue().get(blogKey);
            if (json != null) {
                // 缓存命中，直接使用
                Blog blog = JSONUtil.toBean(json, Blog.class);
                blogs.add(blog);
            } else {
                // 缓存未命中，记录ID稍后从数据库批量查询
                missingIds.add(blogId);
            }
        }

        // 5. 对于缓存未命中的博客，从数据库查询
        if (!missingIds.isEmpty()) {
            log.info("部分博客详情缓存未命中，从数据库补充查询");
            String idStr = StrUtil.join(",", missingIds);
            List<Blog> dbBlogs = query()
                    .in("id", missingIds)
                    .last("ORDER BY FIELD(id," + idStr + ")")
                    .list();

            // 查询作者信息
            for (Blog blog : dbBlogs) {
                queryBlogUser(blog);
                // 使用排行榜中的点赞数
                blog.setLiked(scoreMap.get(blog.getId()));
                // 添加到结果列表
                blogs.add(blog);
                
                // 将查询结果写入缓存
                String blogKey = RedisConstants.BLOG_LIKED_KEY + blog.getId();
                stringRedisTemplate.opsForValue().set(blogKey, JSONUtil.toJsonStr(blog), 30, TimeUnit.MINUTES);
            }
        }

        // 6. 排序，保证按照排行榜顺序
        blogs.sort((a, b) -> {
            // 根据点赞数倒序排序
            return scoreMap.get(b.getId()) - scoreMap.get(a.getId());
        });

        // 7. 查询当前用户是否点赞
        blogs.forEach(this::isBlogLiked);

        return Result.ok(blogs);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
    private void isBlogLiked(Blog blog) {
        //获取当前登陆用户
        UserDTO user = UserHolder.getUser();
        if (user==null){
            return;
        }
        Long userId = user.getId();
        //判断当前用户时候点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }

    @Scheduled(cron = "0 0 */1 * * ?") // 每小时执行一次
    public void syncLikedCountToDb() {
        Set<String> keys = stringRedisTemplate.keys(BLOG_LIKED_COUNT + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            try {
                String blogId = key.substring(BLOG_LIKED_COUNT.length());
                Double score = stringRedisTemplate.opsForZSet().score(key, blogId);
                if (score != null) {
                    update().eq("id", blogId)
                            .set("liked", score.intValue())
                            .update();
                }
            } catch (Exception e) {
                log.error("同步点赞数据失败", e);
            }
        }
    }


}
