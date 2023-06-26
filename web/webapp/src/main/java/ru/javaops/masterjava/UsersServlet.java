package ru.javaops.masterjava;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import ru.javaops.masterjava.common.web.ThymeleafUtil;
import ru.javaops.masterjava.persist.DBIProvider;
import ru.javaops.masterjava.persist.dao.UserDao;
import ru.javaops.masterjava.persist.model.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet(urlPatterns = "/", loadOnStartup = 1)
public class UsersServlet extends HttpServlet {
    UserDao userDao = DBIProvider.getDao(UserDao.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        List<User> users = userDao.getWithLimit(20);
        final WebContext webContext = new WebContext(req, resp, req.getServletContext(), req.getLocale());
        webContext.setVariable("users", users);
        TemplateEngine templateEngine = ThymeleafUtil.getTemplateEngine(getServletContext());
        templateEngine.process("users", webContext, resp.getWriter());
    }
}
