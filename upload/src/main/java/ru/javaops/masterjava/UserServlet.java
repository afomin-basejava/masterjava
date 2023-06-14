package ru.javaops.masterjava;

import org.thymeleaf.context.WebContext;
import ru.javaops.masterjava.model.User;
import ru.javaops.masterjava.model.UserFlag;
import ru.javaops.masterjava.xml.schema.ObjectFactory;
import ru.javaops.masterjava.xml.util.JaxbParser;
import ru.javaops.masterjava.xml.util.StaxStreamProcessor;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
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
        ThymeleafEngine.process(response, "upload", ctx);
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
//        List<User> users = getUsersByStax(request);
        List<User> users = null;
        try {
            users = getUsersByStaxJaxb(request);
            ctx.setVariable("users", users);
            ctx.setVariable("file", request.getPart("file").getSubmittedFileName());
            ThymeleafEngine.process(response, "users", ctx);
        } catch (ServletException | IOException | XMLStreamException | JAXBException e) {
            ctx.setVariable("exception", e);
            String fileName = request.getPart("file").getSubmittedFileName();
            ctx.setVariable("file", "".equals(fileName) ? "File is not selected" : fileName);
            ThymeleafEngine.process(response, "exception", ctx);
            e.printStackTrace();
        }
    }

    private List<User> getUsersByStax(HttpServletRequest request) throws IOException, ServletException {
        List<User> users = new ArrayList<>();
        try (InputStream inputStream = request.getPart("file").getInputStream();
             StaxStreamProcessor staxProcessor = new StaxStreamProcessor(inputStream)) {
            while (staxProcessor.doUntil(XMLEvent.START_ELEMENT, "User")) {
                XMLStreamReader reader = staxProcessor.getReader();
                String email = reader.getAttributeValue("", "email");
                String flag = reader.getAttributeValue("", "flag");
                String fullName = reader.getElementText();
                users.add(new User(fullName, email, UserFlag.valueOf(flag)));
//            reader.close();
            }
        } catch (XMLStreamException e) {
            LOGGER.log(Level.INFO, "Error while reading file: " + request.getPart("file").getSubmittedFileName());
            e.printStackTrace();
        }
        return users;
    }

    private List<User> getUsersByStaxJaxb(HttpServletRequest request) throws ServletException, IOException, XMLStreamException, JAXBException {
        JaxbParser jaxbParser = new JaxbParser(ObjectFactory.class);
        List<User> users = new ArrayList<>();
        try (InputStream inputStream = request.getPart("file").getInputStream();
             StaxStreamProcessor staxProcessor = new StaxStreamProcessor(inputStream)) {
            while (staxProcessor.doUntil(XMLEvent.START_ELEMENT, "User")) {
                XMLStreamReader reader = staxProcessor.getReader();
                JAXBContext jaxbContext = JAXBContext.newInstance("ru.javaops.masterjava.xml.schema");
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                JAXBElement<ru.javaops.masterjava.xml.schema.User> xmlUser = unmarshaller.unmarshal(reader, ru.javaops.masterjava.xml.schema.User.class);
                users.add(new User(xmlUser.getValue().getValue(),
                        xmlUser.getValue().getEmail(),
                        UserFlag.valueOf(xmlUser.getValue().getFlag().value())));
//            reader.close();
            }
        }
        return users;
    }
}
  