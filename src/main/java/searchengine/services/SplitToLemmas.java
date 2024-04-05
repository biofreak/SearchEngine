package searchengine.services;


import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;

public class SplitToLemmas {

    private final LuceneMorphology luceneMorphology;
    private static final String NON_ALPHABET_REGEX = "[^а-яА-Я]";
    private static final String SPACE_REGEX = "(\\s|\\z)";
    private static final String WEEK_REGEX = "(\\s|$)((пн|вт|ср|чт|пт|сб|вс)(\\s|$))+";
    private static final String SINGLE_REGEX = "(\\s|$)(([а-яА-Я])(\\s|$))+";
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};

    public static SplitToLemmas getInstance() throws IOException {
        LuceneMorphology morphology= new RussianLuceneMorphology();
        return new SplitToLemmas(morphology);
    }

    private SplitToLemmas(LuceneMorphology luceneMorphology) {
        this.luceneMorphology = luceneMorphology;
    }

    public Map<String,Long> splitTextToLemmas(String text) {
        return text.lines()
                .map(string -> string
                        .replaceAll("(-$)", "")
                        .replaceAll(NON_ALPHABET_REGEX, " ")
                        .replaceAll("(\\s+|$)", " ")
                        .replaceAll(WEEK_REGEX, " ")
                        .replaceAll(SINGLE_REGEX, " ")
                        .split(SPACE_REGEX))
                .flatMap(Stream::of)
                .map(String::toLowerCase)
                .map(String::strip)
                .filter(Predicate.not(String::isBlank))
                .filter(string -> !anyWordBaseBelongToParticle(luceneMorphology.getMorphInfo(string)))
                .map(luceneMorphology::getNormalForms)
                .filter(Predicate.not(List::isEmpty))
                .map(list -> list.get(0))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    public String removeHtmlTags(String text) {
        return Jsoup.parse(String.valueOf(text)).getAllElements().textNodes().stream()
                .map(TextNode::text)
                .filter(string -> !string.isBlank()).collect(Collectors.joining(" "));
    }
}
