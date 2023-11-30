package dev.lukebemish.flix;

import org.gradle.api.attributes.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FpkgCompatabilityRule implements AttributeCompatibilityRule<LibraryElements> {
    private static final Set<String> FLIX_CLASSES = Set.of(FlixGradlePlugin.FPKG_ELEMENT, LibraryElements.JAR);

    @Override
    public void execute(@NotNull CompatibilityCheckDetails<LibraryElements> details) {
        if (details.getProducerValue() == null || details.getConsumerValue() == null) {
            return;
        }
        String consumer = details.getConsumerValue().getName();
        String producer = details.getProducerValue().getName();
        if (consumer.equals(FlixGradlePlugin.FLIX_CLASSES_ELEMENT) && FLIX_CLASSES.contains(producer)) {
            details.compatible();
        }
    }

    public static class Disambiguation implements AttributeDisambiguationRule<LibraryElements> {

        @Override
        public void execute(MultipleCandidatesDetails<LibraryElements> details) {
            if (details.getConsumerValue() == null) {
                return;
            }
            String consumer = details.getConsumerValue().getName();
            if (consumer.equals(FlixGradlePlugin.FLIX_CLASSES_ELEMENT)) {
                details.getCandidateValues().stream().filter(i -> i.getName().equals(FlixGradlePlugin.FPKG_ELEMENT)).findFirst().ifPresent(details::closestMatch);
            }
        }
    }
}
