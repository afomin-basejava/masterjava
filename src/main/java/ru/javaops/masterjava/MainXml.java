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
        WHILE_HAS_NEXT_UP:
        while (parser.hasNext()) {
            if (parser.next() == XMLStreamReader.START_ELEMENT) {
                if (parser.getLocalName().equalsIgnoreCase("project")) {
                    if (parser.getAttributeValue(null, "name").equals(projectName)) {
                        Set<String> groups = new HashSet<>();
                        projectGroups.put(projectName, groups);
                        INSIDE_GROUP:
                        while (parser.hasNext()) {
                            int eventType = parser.next();
                            if (eventType == XMLStreamReader.START_ELEMENT) {
                                if (parser.getLocalName().equalsIgnoreCase("group")) {
                                    String groupName = parser.getAttributeValue(null, "name");
                                    groups.add(groupName);
                                }
                            }
                            if (eventType == XMLStreamReader.END_ELEMENT) {
                                if (parser.getLocalName().equalsIgnoreCase("project")) {
                                    break INSIDE_GROUP;
                                }
                            }
                        }
                    }
                }
                if (parser.getLocalName().equalsIgnoreCase("user")) {
                    String groups = parser.getAttributeValue(null, "userGroup");
                    INSIDE_USER:
                    while (parser.hasNext()) {
                        int event = parser.next();
                        switch (event) {
                            case XMLStreamReader.START_ELEMENT:
                                String elementText = parser.getElementText();
                                if (groups != null && !groups.isEmpty()) {
                                    Set<String> set = Arrays.stream(groups.split(" ")).collect(Collectors.toSet());
                                    userGroups.put(elementText, set);
                                }
                                break;
                            case XMLStreamReader.END_ELEMENT:
                                break INSIDE_USER;
                        }
                    }
                }
            }
        }
        Set<String> projectUsers = new TreeSet<>();
        userGroups.forEach((name, groups) -> {
            Collection<Set<String>> values = projectGroups.values();
            if (!Collections.disjoint(groups, projectGroups.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()))) {
                projectUsers.add(name);
            }
        });
        return projectUsers;
    }
}
