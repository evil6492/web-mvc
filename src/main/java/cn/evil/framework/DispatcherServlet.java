package cn.evil.framework;

import cn.evil.controller.IndexController;
import cn.evil.controller.UserController;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 一个接收所有请求的Servlet,
 * 然后根据Controller类中方法定义的注解GetMapping和PostMapping来决定调用哪个方法
 */
@WebServlet(urlPatterns = "/")
public class DispatcherServlet extends HttpServlet {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    //存储请求路径到某个具体方法的映射
    private Map<String, GetDispatcher> getMappings = new HashMap<>();
    private Map<String, PostDispatcher> postMappings = new HashMap<>();

    // TODO: 可指定package并自动扫描:
    //添加Controller类
    private List<Class<?>> controllers = new ArrayList<Class<?>>() {{
        add(IndexController.class);
        add(UserController.class);
    }};

    private ViewEngine viewEngine;

    //添加Get请求包含的数据类型，int，long等是get请求可能携带的参数类型
    private static final Set<Class<?>> supportedGetParameterTypes = new HashSet<Class<?>>() {{
        add(int.class);
        add(long.class);
        add(boolean.class);
        add(String.class);
        add(HttpServletRequest.class);
        add(HttpServletResponse.class);
        add(HttpSession.class);
    }};
    //添加Post请求包含的数据类型
    private static final Set<Class<?>> supportedPostParameterTypes = new HashSet<Class<?>>() {{
        add(HttpServletRequest.class);
        add(HttpServletResponse.class);
        add(HttpSession.class);
    }};

    //初始化
    @Override
    public void init() throws ServletException {
        logger.info("init{}...", getClass().getSimpleName());
        ObjectMapper objectMapper = new ObjectMapper();
        //反序列化的时候如果多了其他属性,不抛出异常
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        //依次处理每个Controller
        for (Class<?> controllerClass : controllers) {
            try {
                //创建Controller类实例
                Object controllerInstance = controllerClass.getConstructor().newInstance();
                // 依次处理每个Method:
                for (Method method : controllerClass.getMethods()) {
                    if (method.getAnnotation(GetMapping.class) != null) {
                        // 处理@Get
                        if (method.getReturnType() != ModelAndView.class && method.getReturnType() != void.class) {
                            //如果方法的返回类型不是Model和void则抛出异常
                            throw new UnsupportedOperationException(
                                    "Unsupported return type: " + method.getReturnType() + " for method: " + method);
                        }
                        //处理方法参数类型
                        for (Class<?> parameterClass : method.getParameterTypes()) {
                            if (!supportedGetParameterTypes.contains(parameterClass)) {
                                //如果参数类型不属于定义好的类型集合，抛出异常
                                throw new UnsupportedOperationException(
                                        "Unsupported parameter type: " + parameterClass + " for method: " + method);
                            }
                        }
                        //获取所有参数名，和方法对应的请求路径
                        String[] parameterNames = Arrays.stream(method.getParameters()).map(p -> p.getName())
                                .toArray(String[]::new);
                        String path = method.getAnnotation(GetMapping.class).value();
                        logger.info("Found GET: {} => {}", path, method);
                        //存储请求路径和其对应的方法的所有信息
                        this.getMappings.put(path, new GetDispatcher(controllerInstance, method, parameterNames,
                                method.getParameterTypes()));

                    } else if (method.getAnnotation(PostMapping.class) != null) {
                        // 处理@Post:
                        if (method.getReturnType() != ModelAndView.class && method.getReturnType() != void.class) {
                            throw new UnsupportedOperationException(
                                    "Unsupported return type: " + method.getReturnType() + " for method: " + method);
                        }
                        //处理请求参数是JavaBean的情况
                        Class<?> requestBodyClass = null;
                        for (Class<?> parameterClass : method.getParameterTypes()) {
                            if (!supportedPostParameterTypes.contains(parameterClass)) {
                                if (requestBodyClass == null) {
                                    requestBodyClass = parameterClass;
                                } else {
                                    throw new UnsupportedOperationException("Unsupported duplicate request body type: "
                                            + parameterClass + " for method: " + method);
                                }
                            }
                        }
                        String path = method.getAnnotation(PostMapping.class).value();
                        logger.info("Found POST: {} => {}", path, method);
                        this.postMappings.put(path, new PostDispatcher(controllerInstance, method,
                                method.getParameterTypes(), objectMapper));

                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new ServletException(e);
            }
            //创建ViewEngine
            this.viewEngine = new ViewEngine(getServletContext());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp, this.getMappings);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        process(req, resp, this.postMappings);
    }

    //处理客户端发送的请求
    private void process(HttpServletRequest req, HttpServletResponse resp,
                         Map<String, ? extends AbstractDispatcher> dispatcherMap) throws ServletException, IOException {
        resp.setContentType("text/html");
        resp.setCharacterEncoding("UTF-8");
        String path = req.getRequestURI().substring(req.getContextPath().length());
        //根据路径查找GetDispatcher:
        AbstractDispatcher dispatcher = dispatcherMap.get(path);
        if (dispatcher == null) {
            //未找到，返回404;
            resp.sendError(404);
            return;
        }
        //调用Controller方法获得返回值
        ModelAndView mv = null;
        try {
            mv = dispatcher.invoke(req, resp);
        } catch (ReflectiveOperationException e) {
            throw new ServletException(e);
        }
        //允许返回null
        if (mv == null) {
            return;
        }
        //允许返回"redirect:" 开头的view表示重定向：
        if (mv.view.startsWith("redirect:")) {
            resp.sendRedirect(mv.view.substring(9));
            return;
        }
        //将模板引擎渲染的内容写入响应
        PrintWriter pw = resp.getWriter();
        this.viewEngine.render(mv, pw);
        pw.flush();
    }
}

abstract class AbstractDispatcher {

    public abstract ModelAndView invoke(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ReflectiveOperationException;
}

//处理Get请求
class GetDispatcher extends AbstractDispatcher {

    final Object instance; // Controller实例
    final Method method; // Controller方法
    final String[] parameterNames; // 方法参数名称
    final Class<?>[] parameterClasses; // 方法参数类型

    public GetDispatcher(Object instance, Method method, String[] parameterNames, Class<?>[] parameterClasses) {
        super();
        this.instance = instance;
        this.method = method;
        this.parameterNames = parameterNames;
        this.parameterClasses = parameterClasses;
    }

    @Override
    public ModelAndView invoke(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ReflectiveOperationException {
        //处理请求传递的参数，并传入方法中
        Object[] arguments = new Object[parameterClasses.length];
        for (int i = 0; i < parameterClasses.length; i++) {
            String parameterName = parameterNames[i];
            Class<?> parameterClass = parameterClasses[i];
            if (parameterClass == HttpServletRequest.class) {
                arguments[i] = request;
            } else if (parameterClass == HttpServletResponse.class) {
                arguments[i] = response;
            } else if (parameterClass == HttpSession.class) {
                arguments[i] = request.getSession();
            } else if (parameterClass == int.class) {
                arguments[i] = Integer.valueOf(getOrDefault(request, parameterName, "0"));
            } else if (parameterClass == long.class) {
                arguments[i] = Long.valueOf(getOrDefault(request, parameterName, "0"));
            } else if (parameterClass == boolean.class) {
                arguments[i] = Boolean.valueOf(getOrDefault(request, parameterName, "false"));
            } else if (parameterClass == String.class) {
                arguments[i] = getOrDefault(request, parameterName, "");
            } else {
                throw new RuntimeException("Missing handler for type: " + parameterClass);
            }
        }
        //调用Controller的方法
        return (ModelAndView) this.method.invoke(this.instance, arguments);
    }

    private String getOrDefault(HttpServletRequest request, String name, String defaultValue) {
        String s = request.getParameter(name);
        return s == null ? defaultValue : s;
    }
}

class PostDispatcher extends AbstractDispatcher {

    final Object instance; // Controller实例
    final Method method; // Controller方法
    final Class<?>[] parameterClasses; // 方法参数类型
    final ObjectMapper objectMapper; // JSON映射

    public PostDispatcher(Object instance, Method method, Class<?>[] parameterClasses, ObjectMapper objectMapper) {
        this.instance = instance;
        this.method = method;
        this.parameterClasses = parameterClasses;
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelAndView invoke(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ReflectiveOperationException {
        Object[] arguments = new Object[parameterClasses.length];
        for (int i = 0; i < parameterClasses.length; i++) {
            Class<?> parameterClass = parameterClasses[i];
            if (parameterClass == HttpServletRequest.class) {
                arguments[i] = request;
            } else if (parameterClass == HttpServletResponse.class) {
                arguments[i] = response;
            } else if (parameterClass == HttpSession.class) {
                arguments[i] = request.getSession();
            } else {
                //将传递的Json字符串转换成JavaBean对象
                BufferedReader reader = request.getReader();
                arguments[i] = this.objectMapper.readValue(reader, parameterClass);
            }
        }
        //调用Controller的方法
        return (ModelAndView) this.method.invoke(instance, arguments);
    }
}


