package com.questify.config.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Baseline content filter for Quest title/description:
 * - NSFW/sexual content
 * - General vulgarities
 * - "Brainrot" meme spam terms
 *
 * Notes:
 *  - Uses case-insensitive matching after normalization
 *  - Handles simple leetspeak (3->e, 4->a, 0->o, 1/!->i/l, 5->$->s, 7->t)
 *  - Collapses repeated punctuation/spacing
 */
public class NoProfanityValidator implements ConstraintValidator<NoProfanity, String> {

    private static final Set<String> BANNED_WORDS = Set.of(
            "fuck", "shit", "bitch", "bastard", "asshole", "dickhead", "dumbass",
            "prick", "wanker", "cunt", "slut", "whore", "motherfucker",

            "sex", "sexy", "porn", "pornhub", "hentai", "milf", "futanari",
            "penis", "dick", "cock", "pussy", "vagina", "boobs", "tits", "nudes", "nude", "naked",
            "clit", "clitoris", "balls", "testicles", "scrotum", "erection",
            "orgasm", "ejaculate", "cum", "semen", "anal", "blowjob", "bj", "handjob",
            "fap", "thot",

            "skibidi", "gyatt", "rizz", "sigma", "sigma male", "fanum", "fanum tax", "npc"

    );


    private static final List<Pattern> BANNED_PHRASES = List.of(

            Pattern.compile("(?i)\\bjack\\s*[- ]?\\s*off\\b"),
            Pattern.compile("(?i)\\bjerk\\s*[- ]?\\s*off\\b"),
            Pattern.compile("(?i)\\bblow\\s*[- ]?\\s*job\\b"),
            Pattern.compile("(?i)\\bhand\\s*[- ]?\\s*job\\b"),
            Pattern.compile("(?i)\\bsex\\s*[- ]?\\s*tape\\b"),
            Pattern.compile("(?i)\\bonly\\s*[- ]?\\s*fans\\b"),
            Pattern.compile("(?i)\\bsex\\s*[- ]?\\s*work\\b"),
            Pattern.compile("(?i)\\bnsfw\\b"),
            Pattern.compile("(?i)\\bsigma\\s*[- ]?\\s*male\\b"),
            Pattern.compile("(?i)\\bfanum\\s*[- ]?\\s*tax\\b"),
            Pattern.compile("(?i)\\bskibidi\\s*[- ]?\\s*toilet\\b")
    );

    private static final Set<String> STRICT_WORD_BOUNDARY = Set.of(
            "cum", "bj"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;

        final String norm = normalize(value);

        for (Pattern p : BANNED_PHRASES) {
            if (p.matcher(norm).find()) {
                return false;
            }
        }

        String[] tokens = norm.split("[^a-z0-9]+");
        Set<String> tokenSet = Stream.of(tokens)
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        for (String w : STRICT_WORD_BOUNDARY) {
            if (tokenSet.contains(w)) return false;
        }

        for (String w : BANNED_WORDS) {
            if (tokenSet.contains(w) || norm.contains(w)) {
                return false;
            }
        }

        return true;
    }

    private static String normalize(String s) {
        String out = s.toLowerCase();

        out = Normalizer.normalize(out, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");

        out = out
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('!', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('$', 's')
                .replace('7', 't');

        out = out.replaceAll("[_\\-]{2,}", "-");
        out = out.replaceAll("\\s{2,}", " ").trim();

        return out;
    }
}