package cn.evil.controller;

import cn.evil.bean.SignInBean;
import cn.evil.bean.User;
import cn.evil.framework.GetMapping;
import cn.evil.framework.ModelAndView;
import cn.evil.framework.PostMapping;
import com.sun.deploy.net.HttpResponse;


import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller类
 */
public class UserController {

    private Map<String, User> userDatabase = new HashMap<>();
    public UserController(){
        List<User> users=new ArrayList<>();
        users.add(new User("evil6492@example.com", "123456", "evil6492", "This is evil6492."));
        users.add(new User("tom@example.com", "tomcat", "Tom", "This is tom."));
        for(User user:users){
            userDatabase.put(user.email,user);
        }
    }

    @GetMapping("/signin")
    public ModelAndView signin(){
        return new ModelAndView("/signin.html");
    }

    @PostMapping("/signin")
    public ModelAndView doSignin(SignInBean bean, HttpServletResponse res, HttpSession session) throws IOException {
        User user=userDatabase.get(bean.email);
        if(user==null|| !user.password.equals(bean.password)){
            res.setContentType("application/json");
            PrintWriter pw=res.getWriter();
            pw.write("{\"error\":\"Bad email or password\"}");
            pw.flush();
        }else{
            session.setAttribute("user",user);
            res.setContentType("application/json");
            PrintWriter pw=res.getWriter();
            pw.write("{\"result\":true}");
            pw.flush();
        }
        return null;
    }

    @GetMapping("/signout")
    public ModelAndView signout(HttpSession session) {
        session.removeAttribute("user");
        return new ModelAndView("redirect:/");
    }

    @GetMapping("/user/profile")
    public ModelAndView profile(HttpSession session){
        User user=(User)session.getAttribute("user");
        if(user==null){
            return new ModelAndView("redirect:/signin");
        }
        return new ModelAndView("/profile.html","user",user);
    }
}
