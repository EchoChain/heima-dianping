package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    // 分页查询blog
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        // 这里不建议循环里面查数据库，对数据库压力太大了
        records.forEach((blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        }));
        return Result.ok(records);
    }

    // 根据id查询blog详情
    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        // 2.查询blog有关的用户
        queryBlogUser(blog);
        // 3.判断是否点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        String userId = UserHolder.getUser().getId().toString();
        String key = "blog:liked:" + id;

        // zscore key member
        Double score = redisTemplate.opsForZSet().score(key, userId);
        if (score == null) {
            // 1.未点赞
            // 数据库liked+1 缓存set添加userId
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                redisTemplate.opsForZSet().add(key, userId, System.currentTimeMillis());
            }
        } else {
            // 2.已点赞
            // 数据库liked-1 缓存set移除userId
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                redisTemplate.opsForZSet().remove(key, userId);
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String userId = UserHolder.getUser().getId().toString();
        String key = "blog:liked:" + id;
        // zrange key 0 4
        // 1.查询top5点赞的用户
        Set<String> userSet = redisTemplate.opsForZSet().reverseRange(key, 0, 4);
        if (userSet == null || userSet.isEmpty()) {
            return Result.fail("点赞用户为空");
        }
        // 2.解析出用户Id
        List<Long> idList = userSet.stream()
                .map(Long::parseLong)
                .toList();
        // 3.根据用户id查询用户
//        List<UserDTO> dtoList = userService.listByIds(idList).stream()
//                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
//                .toList();
        // WHERE id IN (id1,id2...) ORDER BY FIELD(id, id1,id2...)
        String idStr = StrUtil.join(",", idList);
        List<UserDTO> dtoList = userService.query()
                .in("id", idList)
                .last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        ArrayList<UserDTO> dtoArrayList = new ArrayList<>(dtoList);
        Collections.reverse(dtoArrayList);
        // 4.返回
        return Result.ok(dtoArrayList);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        String userId = user.getId().toString();
        String key = "blog:liked:" + blog.getId();
        Double score = redisTemplate.opsForZSet().score(key, userId);
        blog.setIsLike(score != null);
    }
}
