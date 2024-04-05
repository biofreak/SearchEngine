package searchengine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import searchengine.services.SplitToLemmas;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

public class Main {
    public static void main(String[] args) {
        StringBuilder text = new StringBuilder();

        try {
            Files.readAllLines(Paths.get("/home/cupuyc/Downloads/experiment.html"))
                    .forEach(text::append);
            //SplitToLemmas.getInstance().splitTextToLemmas(text).forEach((a,b) -> System.out.println(a + " - " + b));
            //Properties props = new Properties();
            //try {
                //props.load(Main.class.getResourceAsStream("/application.yaml"));
            //    return props.getProperty(attribute);
            //} catch (IOException e) {
            //    return null;
            //}
            //System.out.println(props.getProperty("spring.config.location"));
            /*System.out.println(text.toString()
                    .replaceAll("[^а-яА-Я]", " ")
                    .replaceAll("(\\s+|$)", " ")
                    .toLowerCase()
            );*/

            Jsoup.parse(String.valueOf(text)).getAllElements().stream().map(Element::ownText)
                    .filter(Predicate.not(String::isEmpty))
                    .flatMap(string -> Arrays.stream(string.split("(\\.(\\s|\\z)|\\z)")))
                    .forEach(System.out::println);

            /*SplitToLemmas splitter = SplitToLemmas.getInstance();
            String processed = splitter.removeHtmlTags(String.valueOf(text));
            splitter.splitTextToLemmas(processed).forEach((lemma, amount) -> {
                System.out.println(lemma); split("(\\.\\s+|\\z)"
            });*/
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
