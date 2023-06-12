package ru.javaops.masterjava;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ServletContextTemplateResolver;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ThymeleafEngine {
    private static final TemplateEngine templateEngine = new TemplateEngine();
    private static final ServletContextTemplateResolver resolver = new ServletContextTemplateResolver();
    static {
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setSuffix(".html");
        resolver.setPrefix("WEB-INF/templates/");
        templateEngine.setTemplateResolver(resolver);
    }

    public static void process(HttpServletResponse response, String template, WebContext ctx) throws IOException {
        templateEngine.process(template, ctx, response.getWriter());
    }
}
