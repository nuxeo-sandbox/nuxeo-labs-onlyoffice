package org.nuxeo.ecm.restapi.server.jaxrs;

import java.util.Set;

public interface OnlyOfficeTypes {

    // OnlyOffice documentType values expected by DocsAPI.DocEditor
    // (word/cell/slide/pdf/diagram since v7; the older text/spreadsheet/presentation
    // strings are rejected by OnlyOffice 9.x with a -20 error).

    String SPREADSHEET = "cell";

    Set<String> SPREADSHEET_TYPES = Set.of(
            "application/vnd.ms-excel",
            "application/vnd.ms-excel.sheet.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel.template.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template");

    String TEXT = "word";

    Set<String> TEXT_TYPES = Set.of(
            "application/msword",
            "application/vnd.ms-word.document.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-word.template.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template");

    String PRESENTATION = "slide";

    Set<String> PRESENTATION_TYPES = Set.of(
            "application/vnd.ms-powerpoint",
            "application/vnd.ms-powerpoint.presentation.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
            "application/vnd.ms-powerpoint.template.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.presentationml.template",
            "application/vnd.openxmlformats-officedocument.presentationml.slide");

}
