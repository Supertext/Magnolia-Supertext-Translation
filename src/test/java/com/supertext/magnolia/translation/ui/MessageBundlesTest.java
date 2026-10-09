package com.supertext.magnolia.translation.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.supertext.magnolia.translation.api.SupertextClient;
import com.supertext.magnolia.translation.api.SupertextException;
import com.supertext.magnolia.translation.api.SupertextLinks;

/**
 * The interface strings (src/main/resources/supertext-translation/i18n) are complete in English,
 * German, French and Italian, and every key the module uses exists.
 */
class MessageBundlesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");
    private static final String KEY = "supertext-translation\\.[A-Za-z]+(?:\\.[A-Za-z]+)*";

    static Properties bundle(String language) {
        String path = "/supertext-translation/i18n/module-supertext-translation-messages_" + language + ".properties";
        try (InputStream in = MessageBundlesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            Properties properties = new Properties();
            // Magnolia reads its message bundles as UTF-8.
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Like Magnolia's SimpleTranslator: MessageFormat only when there are arguments. */
    static SupertextMessages.Translator translator(String language) {
        Properties properties = bundle(language);
        return (key, args) -> {
            String text = properties.getProperty(key, key);
            return args.length == 0 ? text : new MessageFormat(text, Locale.forLanguageTag(language)).format(args);
        };
    }

    private static Set<String> placeholders(String text) {
        Matcher m = PLACEHOLDER.matcher(text);
        Set<String> found = new TreeSet<>();
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "fr", "it"})
    void sameKeysAndPlaceholdersAsEnglish(String language) {
        Properties en = bundle("en");
        Properties other = bundle(language);
        assertEquals(new TreeSet<>(en.stringPropertyNames()), new TreeSet<>(other.stringPropertyNames()), language);
        for (String key : en.stringPropertyNames()) {
            String value = other.getProperty(key);
            assertFalse(value.isBlank(), language + " " + key);
            assertEquals(placeholders(en.getProperty(key)), placeholders(value), language + " " + key);
            assertEquals(en.getProperty(key).contains("<a "), value.contains("<a "), language + " " + key);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "fr", "it"})
    void messagesWithArgumentsSurviveMessageFormat(String language) {
        Properties p = bundle(language);
        for (String key : p.stringPropertyNames()) {
            String value = p.getProperty(key);
            if (PLACEHOLDER.matcher(value).find()) {
                // A straight apostrophe starts a quoted section in MessageFormat and eats the placeholders.
                assertFalse(value.contains("'"), language + " " + key + ": use ’ instead of '");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "fr", "it"})
    void apiKeyHintKeepsBothLinks(String language) {
        SupertextMessages messages = new SupertextMessages(translator(language));
        String plain = messages.hint();
        String html = messages.htmlHint();
        for (String text : new String[] {plain, html}) {
            assertTrue(text.contains(SupertextLinks.SIGNUP_URL), language + ": " + text);
            assertTrue(text.contains(SupertextLinks.API_KEY_URL), language + ": " + text);
            assertTrue(text.contains("Integrations → API"), language + ": " + text);
            assertTrue(text.contains("Admin"), language + ": " + text);
        }
        assertEquals(2, html.split("target=\"_blank\" rel=\"noopener\"", -1).length - 1, language);
    }

    @Test
    void frenchPunctuationHasNonBreakingSpaces() {
        Properties fr = bundle("fr");
        for (String key : fr.stringPropertyNames()) {
            String value = fr.getProperty(key);
            assertFalse(Pattern.compile(" [?!;:]|« | »").matcher(value).find(), key + ": " + value);
        }
    }

    /** Every key in the module's Java code and YAML definitions is in the English bundle. */
    @Test
    void everyUsedKeyExists() throws IOException {
        Properties en = bundle("en");
        Set<String> used = new TreeSet<>();
        Pattern key = Pattern.compile("\"(" + KEY + ")\"|:\\s*(" + KEY + ")\\s*$", Pattern.MULTILINE);
        Pattern errorKey = Pattern.compile("(?:error\\(|KEY_PREFIX \\+ |key = )\"([A-Za-z]+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") || f.toString().endsWith(".yaml")).collect(Collectors.toList())) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = key.matcher(source);
                while (m.find()) {
                    used.add(m.group(1) != null ? m.group(1) : m.group(2));
                }
                Matcher e = errorKey.matcher(source);
                while (e.find()) {
                    used.add(SupertextClient.KEY_PREFIX + e.group(1));
                }
            }
        }
        used.remove("supertext-translation.settings.politeness."); // + value, checked below
        used.addAll(Set.of("supertext-translation.settings.politeness.default", "supertext-translation.settings.politeness.more",
                "supertext-translation.settings.politeness.less"));
        assertTrue(used.size() > 50, "found " + used);
        for (String k : used) {
            assertTrue(en.containsKey(k), "missing in the English bundle: " + k);
        }
    }

    @Test
    void errorsAreShownInTheEditorsLanguage() {
        SupertextException e = null;
        try {
            SupertextClient.checkEndpoint("http://example.com/v1/");
        } catch (SupertextException ex) {
            e = ex;
        }
        assertNotNull(e);
        assertTrue(e.getMessage().startsWith("The Supertext endpoint must use https"));
        assertEquals("Der Supertext-Endpunkt muss https verwenden (http ist nur für localhost erlaubt): http://example.com/v1/",
                new SupertextMessages(translator("de")).of(e));
        assertEquals("L’endpoint Supertext deve usare https (http è consentito solo per localhost): http://example.com/v1/",
                new SupertextMessages(translator("it")).of(e));
    }

    @Test
    void unknownKeyFallsBackToTheEnglishMessage() {
        SupertextException e = new SupertextException("Something else.");
        assertEquals("Something else.", new SupertextMessages(translator("fr")).of(e));
        SupertextMessages none = new SupertextMessages((k, args) -> k);
        assertEquals(SupertextLinks.PLAIN_TEXT_HINT, none.hint());
        assertEquals(SupertextLinks.HTML_HINT, none.htmlHint());
    }
}
