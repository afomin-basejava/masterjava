package ru.javaops.masterjava;

import org.xml.sax.SAXException;
import ru.javaops.masterjava.xml.schema.ObjectFactory;
import ru.javaops.masterjava.xml.schema.Payload;
import ru.javaops.masterjava.xml.schema.Project;
import ru.javaops.masterjava.xml.schema.User;
import ru.javaops.masterjava.xml.util.JaxbParser;
import ru.javaops.masterjava.xml.util.Schemas;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.io.Resources.getResource;

public class MainXml {

    public static void main(String[] args) throws IOException, JAXBException, SAXException, XMLStreamException {
        String projectName = args[0];
        getUsersJaxb(projectName).forEach(user -> System.out.println(user.getFullName()));
        System.out.println("----------------------------------");
        getUsersStax(projectName).forEach(System.out::println);
    }

    private static Set<User> getUsersJaxb(String projectName) throws JAXBException, IOException {
        JaxbParser jaxbParser = new JaxbParser(ObjectFactory.class);
        jaxbParser.setSchema(Schemas.ofClasspath("payload.xsd"));

        Payload payLoad = jaxbParser.unmarshal(getResource("payload.xml").openStream());

        Project project = payLoad.getProjects().getProject().stream().filter(p -> p.getName().equals(projectName)).findFirst()
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectName));

        Set<Project.Group> groups = new HashSet<>(project.getGroup());

        Set<User> users = new HashSet<>(Collections.unmodifiableList(payLoad.getUsers().getUser()));

        return users.stream()
                .filter(user -> !Collections.disjoint(groups, user.getUserGroup()))
                .sorted(Comparator.comparing(User::getFullName))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> getUsersStax(String projectName) throws XMLStreamException, IOException {
        URL url = getResource("payload.xml");
        InputStream is = url.openStream();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader parser = factory.createXMLStreamReader(is);
        Map<String, Set<String>> projectGroups = new HashMap<>();
        Map<String, Set<String>> userGroups = new HashMap<>();
        while (parser.hasNext()) {
            if (parser.next() == XMLStreamReader.START_ELEMENT) {
                if (parser.getLocalName().equalsIgnoreCase("project")) {
                    if (parser.getAttributeValue(null, "name").equals(projectName)) {
                        Set<String> groups = new HashSet<>();
                        projectGroups.put(projectName, groups);
                        INSIDE_GROUP:
                        while (parser.hasNext()) {
                            int eventType = parser.next();
                            switch (eventType) {
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
                    String groupsString = parser.getAttributeValue(null, "userGroup");
                    INSIDE_USER:
                    while (parser.hasNext()) {
                        int event = parser.next();
                        switch (event) {
                            case XMLStreamReader.START_ELEMENT:
                                String user = parser.getElementText();
                                if (groupsString != null && !groupsString.isEmpty()) {
                                    userGroups.put(user, Arrays.stream(groupsString.split(" ")).collect(Collectors.toSet()));
                                }
                                break;
                            case XMLStreamReader.END_ELEMENT:
                                break INSIDE_USER;
                        }
                    }
                }
            }
        }
        Set<String> projectUsers = new TreeSet<>(Comparator.comparing(String::toLowerCase));
        userGroups.forEach((name, groups) -> {
            if (!Collections.disjoint(groups, projectGroups.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()))) {
                projectUsers.add(name);
            }
        });
        return projectUsers;
    }
}
