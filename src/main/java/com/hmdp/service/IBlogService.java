package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogOfFollow(Long max, Integer offset);

    Result queryBlogById(Long id);

    Result queryBlogLikesById(Long id);

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogByLikedTop5();
}
