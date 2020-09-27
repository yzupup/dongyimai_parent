package com.offcn.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.offcn.cart.service.CartService;
import com.offcn.entity.Result;
import com.offcn.group.Cart;

import com.offcn.util.CookieUtil;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartController {
    @Reference(timeout = 6000)
    private CartService cartService;

    /**
     * 购物车列表
     *
     * @param request
     * @return
     */
    @RequestMapping("/findCartList")
    public List<Cart> findCartList(HttpServletRequest request, HttpServletResponse response) {
        //得到登陆人账号,判断当前是否有人登陆
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        System.out.println("username a:" + username);

        //读取本地购物车
        //从cookie中获取购物车信息
        String cartListString = CookieUtil.getCookieValue(request, "cartList", "UTF-8");
        if (cartListString == null || cartListString.equals("")) {
            cartListString = "[]";
        }
        List<Cart> cartList_cookie = JSON.parseArray(cartListString, Cart.class);
        if (username.equals("anonymousUser")) { //如果未登录

            return cartList_cookie;

        } else { //如果已登录，从redis中提取
            List<Cart> cartListFromRedis = cartService.findCartListFromRedis(username);
            //判断coolie取出的购物车是否有信息
            if (cartList_cookie.size() > 0) { //有数据才合并
                //合并购物车
                cartListFromRedis = cartService.mergeCartList(cartListFromRedis, cartList_cookie);
                //清除cookie中的数据
                CookieUtil.deleteCookie(request, response, "cartList");
                //将合并后的数据存入到redis中
                cartService.saveCartListToRedis(username, cartListFromRedis);
            }
            return cartListFromRedis;
        }
    }

    @RequestMapping("/addGoodsToCartList")
    @CrossOrigin(origins = "http://localhost:9105", allowCredentials = "true")
    public Result addGoodsToCartList(HttpServletRequest request, HttpServletResponse response, Long itemId, Integer num) {
        //response.setHeader("Access-Control-Allow-Origin", "http://localhost:9105");
        //response.setHeader("Access-Control-Allow-Credentials", "true");

        //得到登陆人账号,判断当前是否有人登陆
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        System.out.println("当前登录用户 a：" + username);

        try {
            List<Cart> oldcartList = findCartList(request, response);//获取购物车列表
            List<Cart> newcartList = cartService.addGoodsToCartList(oldcartList, itemId, num);

            if (username.equals("anonymousUser")) { //如果是未登录，保存到cookie
                CookieUtil.setCookie(request, response, "cartList", JSON.toJSONString(newcartList), 3600 * 24, "UTF-8");
                System.out.println("未登录，向cookie存入数据");
            } else {//如果是已登录，保存到redis
                cartService.saveCartListToRedis(username, newcartList);
                System.out.println("已登录，数据存入redis");
            }
            return new Result(true, "添加成功");
        } catch (RuntimeException e) {
            e.printStackTrace();
            return new Result(false, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return new Result(false, "添加失败");
        }

    }

}
