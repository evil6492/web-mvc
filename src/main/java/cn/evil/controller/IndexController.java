package cn.evil.controller;

import cn.evil.bean.User;
import cn.evil.framework.GetMapping;
import cn.evil.framework.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.rmi.MarshalledObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller类
 */
public class IndexController {
    @GetMapping("/")
    public ModelAndView index(HttpSession session) {
        User user=(User) session.getAttribute("user");
        Map<String, Object> map=new HashMap<String, Object>();
        map.put("user",user);
        return new ModelAndView("/profile.html", map);
    }

    @GetMapping("/hello")
    public ModelAndView hello(String name){
        if(name==null){
            name="World";
        }
        return new ModelAndView("/hello.html","name",name);
    }
}
