package ru.javaops.masterjava;

import com.google.common.io.Resources;
import j2html.tags.ContainerTag;
import j2html.tags.specialized.HtmlTag;
import org.xml.sax.SAXException;
import ru.javaops.masterjava.xml.schema.ObjectFactory;
import ru.javaops.masterjava.xml.schema.Payload;
import ru.javaops.masterjava.xml.schema.Project;
import ru.javaops.masterjava.xml.schema.User;
import ru.javaops.masterjava.xml.util.JaxbParser;
import ru.javaops.masterjava.xml.util.Schemas;
import ru.javaops.masterjava.xml.util.XsltProcessor;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.TransformerException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.io.Resources.getResource;
import static j2html.TagCreator.*;

public class MainXml {
    private static final URL URL = getResource("payload.xml");

    public static void main(String[] args) throws IOException, JAXBException, SAXException, XMLStreamException, TransformerException {
        String projectName = args[0];
        getProjectUsersJaxb(projectName).forEach(user -> System.out.println(user.getFullName()));
        System.out.println("----------------------------------");
        getUsersStax(projectName).forEach(System.out::println);
        System.out.println("----------------------------------");
        Writer bw = Files.newBufferedWriter(Paths.get("users.html"));
        bw.write(toHtml(getProjectUsersByStax(projectName), projectName).toString());
        bw.close();
        try (Writer writer = Files.newBufferedWriter(Paths.get("groups.html"))) {
            writer.write(xsltTransform(projectName));
        }
    }

    private static Set<User> getProjectUsersJaxb(String projectName) throws JAXBException, IOException {
        JaxbParser jaxbParser = new JaxbParser(ObjectFactory.class);
        jaxbParser.setSchema(Schemas.ofClasspath("payload.xsd"));

        Payload payLoad = jaxbParser.unmarshal(URL.openStream());

        Project project = payLoad.getProjects().getProject().stream().filter(p -> p.getName().equals(projectName)).findFirst()
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectName));

        Set<Project.Group> groups = new HashSet<>(project.getGroup());

        Set<User> users = new HashSet<>(Collections.unmodifiableList(payLoad.getUsers().getUser()));

        return users.stream()
                .filter(user -> !Collections.disjoint(groups, user.getUserGroup()))
                .sorted(Comparator.comparing(user -> user.getFullName().toLowerCase()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> getUsersStax(String projectName) throws XMLStreamException, IOException {
        InputStream is = URL.openStream();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader parser = factory.createXMLStreamReader(is);
        Map<String, Set<String>> projectGroups = new HashMap<>();
        Map<String, Set<String>> userGroups = new HashMap<>();
        Map<User, Set<String>> userGroupsMap = new HashMap<>();
        while (parser.hasNext()) {
            switch (parser.next()) {
                case XMLStreamReader.START_ELEMENT:
                    if (parser.getLocalName().equalsIgnoreCase("project")) {
                        if (parser.getAttributeValue(null, "name").equals(projectName)) {
                            Set<String> groups = new HashSet<>();
                            projectGroups.put(projectName, groups);
                            INSIDE_GROUP:
                            while (parser.hasNext()) {
                                switch (parser.next()) {
                                    case XMLStreamReader.START_ELEMENT:
                                        if (parser.getLocalName().equalsIgnoreCase("group")) {
                                            groups.add(parser.getAttributeValue(null, "name"));
                                        }
                                        break;
                                    case XMLStreamReader.END_ELEMENT:
                                        if (parser.getLocalName().equalsIgnoreCase("project")) {
                                            break INSIDE_GROUP;
                                        }
                                        break;
                                }
                            }
                        }
                    }
                    if (parser.getLocalName().equalsIgnoreCase("user")) {
                        String userGroupsAsString = parser.getAttributeValue(null, "userGroup");
                        String userEmail = parser.getAttributeValue(null, "email");
                        INSIDE_USER:
                        while (parser.hasNext()) {
                            switch (parser.next()) {
                                case XMLStreamReader.START_ELEMENT:
                                    String userName = parser.getElementText();
                                    if (userGroupsAsString != null && !userGroupsAsString.isEmpty()) {
                                        Set<String> groupsOfUser = Arrays.stream(userGroupsAsString.split(" ")).collect(Collectors.toSet());
                                        userGroups.put(userName, groupsOfUser);
                                        User user = new User();
                                        user.setFullName(userName);
                                        user.setEmail(userEmail);
                                        user.setUserGroup(new ArrayList<>(groupsOfUser));
                                        userGroupsMap.put(user, groupsOfUser);
                                    }
                                    break;
                                case XMLStreamReader.END_ELEMENT:
                                    break INSIDE_USER;
                            }
                        }
                    }
                    break;
                case XMLStreamReader.END_DOCUMENT:
                default:
                    break;
            }
        }

        Set<String> projectUsers = new TreeSet<>(Comparator.comparing(String::toLowerCase));
        userGroups.forEach((userName, usersGroups) -> {
            if (!Collections.disjoint(usersGroups, projectGroups.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()))) {
                projectUsers.add(userName);
            }
        });
//        userGroupsMap.forEach((user, groups) -> System.out.printf("user: name = %s, email = %s, groups = %s <-> %s%n",
//                user.getFullName(), user.getEmail(), user.getUserGroup(), groups));
//        System.out.println("-----------");
//        //      4: Сделать реализацию MainXml через StAX (выводить имя/email)
//        Set<User> users = userGroupsMap.keySet();
//        userGroupsMap.entrySet().stream()
//                .filter(entry -> !Collections.disjoint(users.stream()
//                        .map(User::getUserGroup)
//                        .flatMap(Collection::stream)
//                        .collect(Collectors.toSet()), entry.getValue()))
//                .map(Map.Entry::getKey)
//                .collect(Collectors.toList())
//                .stream()
//                .sorted(Comparator.comparing((User user) -> user.getFullName().toLowerCase())
//                        .thenComparing(user -> user.getEmail().toLowerCase()))
//                .forEach(user -> System.out.printf("%20s %20s%n", user.getFullName(), user.getEmail()));
        return projectUsers;
    }

    private static Set<User> getProjectUsersByStax(String projectName) throws XMLStreamException, IOException {
        InputStream is = URL.openStream();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader parser = factory.createXMLStreamReader(is);
        Map<String, Set<String>> projectGroups = new HashMap<>();
        Map<User, Set<String>> userGroupsMap = new HashMap<>();
        while (parser.hasNext()) {
            switch (parser.next()) {
                case XMLStreamReader.START_ELEMENT:
                    if (parser.getLocalName().equalsIgnoreCase("project")) {
                        if (parser.getAttributeValue(null, "name").equals(projectName)) {
                            Set<String> groups = new HashSet<>();
                            projectGroups.put(projectName, groups);
                            INSIDE_GROUP:
                            while (parser.hasNext()) {
                                switch (parser.next()) {
                                    case XMLStreamReader.START_ELEMENT:
                                        if (parser.getLocalName().equalsIgnoreCase("group")) {
                                            groups.add(parser.getAttributeValue(null, "name"));
                                        }
                                        break;
                                    case XMLStreamReader.END_ELEMENT:
                                        if (parser.getLocalName().equalsIgnoreCase("project")) {
                                            break INSIDE_GROUP;
                                        }
                                        break;
                                }
                            }
                        }
                    }
                    if (parser.getLocalName().equalsIgnoreCase("user")) {
                        String userGroupsAsString = parser.getAttributeValue(null, "userGroup");
                        String userEmail = parser.getAttributeValue(null, "email");
                        INSIDE_USER:
                        while (parser.hasNext()) {
                            switch (parser.next()) {
                                case XMLStreamReader.START_ELEMENT:
                                    String userName = parser.getElementText();
                                    if (userGroupsAsString != null && !userGroupsAsString.isEmpty()) {
                                        Set<String> groupsOfUser = Arrays.stream(userGroupsAsString.split(" ")).collect(Collectors.toSet());
                                        User user = new User();
                                        user.setFullName(userName);
                                        user.setEmail(userEmail);
                                        user.setUserGroup(new ArrayList<>(groupsOfUser));
                                        userGroupsMap.put(user, groupsOfUser);
                                    }
                                    break;
                                case XMLStreamReader.END_ELEMENT:
                                    break INSIDE_USER;
                            }
                        }
                    }
                    break;
                case XMLStreamReader.END_DOCUMENT:
                default:
                    break;
            }
        }

        return userGroupsMap.keySet();
    }

    //5: Из списка участников сделать html таблицу (имя/email). Реализация- любая.
    private static HtmlTag toHtml(Set<User> users, String projectName) {
        final ContainerTag table = table().with(
                        tr().with(th("FullName"), th("email")))
                .attr("border", "2")
                .attr("cellpadding", "18")
                .attr("cellspacing", "1");
        users.forEach(u -> table.with(tr().with(td(u.getFullName()), td(u.getEmail()))));
        return html().with(
                head().with(title(projectName + " users")),
                body().with(h1(projectName + " users"), table));
//                .render();
    }

    public static String xsltTransform(String project) throws IOException, TransformerException {
        try (InputStream xslInputStream = Resources.getResource("payload.xsl").openStream();
             InputStream xmlInputStream = Resources.getResource("payload.xml").openStream()) {

            XsltProcessor processor = new XsltProcessor(xslInputStream);
            processor.setParameter("project", project);
            String transform = processor.transform(xmlInputStream);
            return transform;
        }
    }


}
