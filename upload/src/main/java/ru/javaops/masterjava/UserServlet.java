package ru.javaops.masterjava;

import org.thymeleaf.context.WebContext;
import ru.javaops.masterjava.model.User;
import ru.javaops.masterjava.model.UserFlag;
import ru.javaops.masterjava.xml.util.StaxStreamProcessor;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(urlPatterns = "/", loadOnStartup = 1)
@MultipartConfig
public class UserServlet extends HttpServlet {
    private final static Logger LOGGER =
            Logger.getLogger(UserServlet.class.getCanonicalName());
    private static final long serialVersionUID = 2L;

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final WebContext ctx = new WebContext(request, response, request.getServletContext(), request.getLocale());
        UserProccessor.process(response, "upload", ctx);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        final WebContext ctx = new WebContext(request, response, request.getServletContext(), request.getLocale());
//        Collection<Part> parts = request.getParts();
//        for (Part part : parts) {
//            System.out.println("Part Name:" + part.getName());
//            System.out.println("Part: "+part);
//            System.out.println("Header: ");
//            for (String headerName : part.getHeaderNames()) {
//                System.out.println(headerName + " -> " + part.getHeader(headerName));
//            }
//            System.out.println("Part Size: " + part.getSize()+"\n----------------------------");
//            InputStream inputStream = part.getInputStream();
//            Reader reader = new InputStreamReader(inputStream);
//            BufferedReader bufferedReader = new BufferedReader(reader);
//            while(bufferedReader.ready()){
//                String s = bufferedReader.readLine();
//                System.out.println(s);
//            }
//            System.out.println("===============================================");
//        }
        List<User> users = new ArrayList<User>();
        try (InputStream inputStream = request.getPart("file").getInputStream()) {
            StaxStreamProcessor staxProcessor = new StaxStreamProcessor(inputStream);
            while (staxProcessor.doUntil(XMLEvent.START_ELEMENT, "User")) {
                XMLStreamReader reader = staxProcessor.getReader();
                String email = reader.getAttributeValue("", "email");
                String flag = reader.getAttributeValue("", "flag");
                String fullName = reader.getElementText();
                users.add(new User(fullName, email, UserFlag.valueOf(flag)));
//            reader.close();
            }
            staxProcessor.close();
        } catch (XMLStreamException e) {
            LOGGER.log(Level.INFO, "No users: " + request.getPart("file").getSubmittedFileName());
            e.printStackTrace();
        }
        ctx.setVariable("users", users);
        ctx.setVariable("file", request.getPart("file").getSubmittedFileName());
        UserProccessor.process(response, "users", ctx);
    }
}
  