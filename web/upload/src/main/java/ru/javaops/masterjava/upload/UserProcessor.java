package ru.javaops.masterjava.upload;

import org.slf4j.Logger;
import ru.javaops.masterjava.persist.DBIProvider;
import ru.javaops.masterjava.persist.dao.UserDao;
import ru.javaops.masterjava.persist.model.User;
import ru.javaops.masterjava.persist.model.UserFlag;
import ru.javaops.masterjava.xml.schema.ObjectFactory;
import ru.javaops.masterjava.xml.util.JaxbParser;
import ru.javaops.masterjava.xml.util.JaxbUnmarshaller;
import ru.javaops.masterjava.xml.util.StaxStreamProcessor;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.slf4j.LoggerFactory.getLogger;

public class UserProcessor {
    private static final Logger logger = getLogger(UserProcessor.class);
    private static final JaxbParser jaxbParser = new JaxbParser(ObjectFactory.class);
    private static final UserDao userDao = DBIProvider.getDao(UserDao.class);

    public List<User> process(final InputStream is) throws XMLStreamException, JAXBException {
        final StaxStreamProcessor processor = new StaxStreamProcessor(is);
        List<User> users = new ArrayList<>();

        JaxbUnmarshaller unmarshaller = jaxbParser.createUnmarshaller();
        while (processor.doUntil(XMLEvent.START_ELEMENT, "User")) {
            ru.javaops.masterjava.xml.schema.User xmlUser = unmarshaller.unmarshal(processor.getReader(), ru.javaops.masterjava.xml.schema.User.class);
            final User user = new User(xmlUser.getValue(), xmlUser.getEmail(), UserFlag.valueOf(xmlUser.getFlag().value()));
            users.add(user);
        }
        return users;
    }

    public List<User> process(final InputStream is, int chunkSize) throws XMLStreamException, JAXBException {
        final StaxStreamProcessor processor = new StaxStreamProcessor(is);
        List<User> users = new ArrayList<>();

        JaxbUnmarshaller unmarshaller = jaxbParser.createUnmarshaller();
        while (processor.doUntil(XMLEvent.START_ELEMENT, "User")) {
            ru.javaops.masterjava.xml.schema.User xmlUser = unmarshaller.unmarshal(processor.getReader(), ru.javaops.masterjava.xml.schema.User.class);
            final User user = new User(xmlUser.getValue(), xmlUser.getEmail(), UserFlag.valueOf(xmlUser.getFlag().value()));
            users.add(user);
        }
        int[] ints = userDao.insertBatch(users, chunkSize);
        return IntStream.range(0, ints.length).filter(i -> ints[i] == 0).mapToObj(users::get).collect(Collectors.toList());
    }

    public List<AbandonedEmails> processChunk(final InputStream is, int chunkSize) throws XMLStreamException, JAXBException {
        final StaxStreamProcessor processor = new StaxStreamProcessor(is);
        final JaxbUnmarshaller unmarshaller = jaxbParser.createUnmarshaller();
        final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<User> chunk = new ArrayList<>();
        List<AbandonedEmails> abandonedEmails = new ArrayList<>();

        while (processor.doUntil(XMLEvent.START_ELEMENT, "User")) {
            ru.javaops.masterjava.xml.schema.User xmlUser = unmarshaller.unmarshal(processor.getReader(), ru.javaops.masterjava.xml.schema.User.class);
            final User user = new User(xmlUser.getValue(), xmlUser.getEmail(), UserFlag.valueOf(xmlUser.getFlag().value()));
            chunk.add(user);
            if (chunk.size() >= chunkSize) {
                abandonedEmails.addAll(extractAbandonedEmails(chunk, executor));
                chunk = new ArrayList<>();
            }
        }
        abandonedEmails.addAll(extractAbandonedEmails(chunk, executor));
        executor.shutdown();
        return abandonedEmails;
    }

    public List<AbandonedEmails> processChunkGroupedByReason(final InputStream is, int chunkSize) throws XMLStreamException, JAXBException {
        final StaxStreamProcessor processor = new StaxStreamProcessor(is);
        final JaxbUnmarshaller unmarshaller = jaxbParser.createUnmarshaller();
        final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<User> chunk = new ArrayList<>();
        List<AbandonedEmails> abandonedEmails = new ArrayList<>();

        while (processor.doUntil(XMLEvent.START_ELEMENT, "User")) {
            ru.javaops.masterjava.xml.schema.User xmlUser = unmarshaller.unmarshal(processor.getReader(), ru.javaops.masterjava.xml.schema.User.class);
            final User user = new User(xmlUser.getValue(), xmlUser.getEmail(), UserFlag.valueOf(xmlUser.getFlag().value()));
            chunk.add(user);
            if (chunk.size() >= chunkSize) {
                abandonedEmails.addAll(extractAbandonedEmails(chunk, executor));
                chunk = new ArrayList<>();
            }
        }
        abandonedEmails.addAll(extractAbandonedEmails(chunk, executor));
        executor.shutdown();
        return abandonedEmails.stream()
                .collect(Collectors.groupingBy(AbandonedEmails::getReason)).entrySet().stream()
                .map(entry -> new AbandonedEmails(entry.getValue().stream()
                        .map(AbandonedEmails::getEmails)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()), entry.getKey()))
                .collect(Collectors.toList());
    }

    private List<AbandonedEmails> extractAbandonedEmails(List<User> finalChunk, ExecutorService executor) {
        List<AbandonedEmails> abandonedEmailsList = new ArrayList<>();
        CompletableFuture<AbandonedEmails> future = CompletableFuture.supplyAsync(() -> insertChunk(finalChunk), executor)
                .handle((res, ex) -> {
                    if (null != ex) {
                        logger.debug("Failed to insert chunk -> CompletableFuture<AbandonedEmails>");
                        List<String> emailRange = new ArrayList<>();
                        emailRange.add(String.format("%s -range- %s", finalChunk.get(0).getEmail(), finalChunk.get(finalChunk.size() - 1).getEmail()));
                        return new AbandonedEmails(emailRange, ex.getMessage());
                    }
                    return res;
                });
        try {
            AbandonedEmails abandons = future.get();
            if (!abandons.getEmails().isEmpty()) {
                if (abandons.getEmails().contains("user1@gmail.com")) {
                    throw new RuntimeException("CompletableFuture#get exception");
                }
                abandonedEmailsList.add(abandons);
            }
        } catch (Exception ex) {
            logger.debug("Failed to insert chunk -> CompletableFuture#get {}", ex.getMessage());
            List<String> emailRange = new ArrayList<>();
            emailRange.add(String.format("%s -range- %s", finalChunk.get(0).getEmail(), finalChunk.get(finalChunk.size() - 1).getEmail()));
            abandonedEmailsList.add(new AbandonedEmails(emailRange, ex.getMessage()));
        }
        return abandonedEmailsList;
    }

    private AbandonedEmails insertChunk(List<User> chunk) {
        for (User user : chunk) {
            if (user.getFullName().equals("User3")) {
                throw new RuntimeException("insertChunk userDao.insertBatch exception");
            }
        }
        int[] ints = userDao.insertBatch(chunk, chunk.size());
        List<String> existedEmails = IntStream.range(0, ints.length)
                .filter(i -> ints[i] == 0)
                .mapToObj(index -> chunk.get(index).getEmail())
                .collect(Collectors.toList());
        return new AbandonedEmails(existedEmails, "existed emails");
    }

    static class AbandonedEmails {
        List<String> emails;
        String reason;

        public AbandonedEmails(List<String> emails, String reason) {
            this.emails = emails;
            this.reason = reason;
        }

        public List<String> getEmails() {
            return emails;
        }

        public void setEmails(List<String> emails) {
            this.emails = emails;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "AbandonedEmails{" +
                    "emails=" + emails +
                    ", reason='" + reason + '\'' +
                    '}';
        }
    }
}
