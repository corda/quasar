package co.paralleluniverse.common.resource;

import java.util.List;

@FunctionalInterface
interface TemplateInterface {
    @SuppressWarnings("unused")
    List<String> getNames();
}
