package searchengine.utils;


import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class SplitToLemmas {

    private final LuceneMorphology luceneMorphology;
    private static String nonAlphabetRegex;
    private static final String SPACE_REGEX = "(\\s|\\z)";
    private static final String WEEK_REGEX = "(\\s|$)((пн|вт|ср|чт|пт|сб|вс)(\\s|$))+";
    private static final String SINGLE_REGEX = "(\\s|$)(([а-яА-Яa-zA-Z])(\\s|$))+";
    private static String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};

    public static SplitToLemmas getInstanceRus() throws IOException {
        LuceneMorphology morphologyRus = new RussianLuceneMorphology();
        nonAlphabetRegex = "[^а-яА-Я]";
        particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};
        return new SplitToLemmas(morphologyRus);
    }

    public static SplitToLemmas getInstanceEng() throws IOException {
        LuceneMorphology morphologyEng = new EnglishLuceneMorphology();
        nonAlphabetRegex = "[^a-zA-Z]";
        particlesNames = new String[]{"INT", "PREP", "CONJ", "ARTICLE", "PART"};
        return new SplitToLemmas(morphologyEng);
    }

    private SplitToLemmas(LuceneMorphology luceneMorphology) {
        this.luceneMorphology = luceneMorphology;
    }

    public Map<String,Long> splitTextToLemmas(String text) {
        return removeHtmlTags(text).lines()
                .map(string -> string
                        .replaceAll("(-$)", "")
                        .replaceAll(nonAlphabetRegex, " ")
                        .replaceAll("(\\s+|$)", " ")
                        .replaceAll(WEEK_REGEX, " ")
                        .replaceAll(SINGLE_REGEX, " ")
                        .split(SPACE_REGEX))
                .flatMap(Stream::of)
                .map(String::toLowerCase)
                .map(String::strip)
                .filter(Predicate.not(String::isBlank))
                .filter(string -> !anyWordBaseBelongToParticle(getMorphInfo(string)))
                .map(this::getNormalForms)
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

    public List<String> getNormalForms(String word) {
        return luceneMorphology.getNormalForms(word);
    }

    public List<String> getMorphInfo(String word) {
        return luceneMorphology.getMorphInfo(word);
    }

    public String removeHtmlTags(String text) {
        return Jsoup.parse(text).getAllElements().stream().map(Element::ownText)
                .filter(string -> !string.isBlank()).collect(Collectors.joining(" "));
    }
}
