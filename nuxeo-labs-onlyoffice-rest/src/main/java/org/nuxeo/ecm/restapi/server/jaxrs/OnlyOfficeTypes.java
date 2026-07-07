package org.nuxeo.ecm.restapi.server.jaxrs;

import java.util.Set;

public interface OnlyOfficeTypes {

    // OnlyOffice documentType values expected by DocsAPI.DocEditor
    // (word/cell/slide/pdf/diagram since v7; the older text/spreadsheet/presentation
    // strings are rejected by OnlyOffice 9.x with a -20 error).
    //
    // Mime-type sets below cover the Microsoft Office family AND the OpenDocument
    // (OpenOffice/LibreOffice) family, plus a few plain-text formats ONLYOFFICE
    // supports natively. Keep in sync with `Nuxeo.OnlyOfficeBehavior` in
    // `onlyoffice-behavior.html` (JS side).

    String SPREADSHEET = "cell";

    Set<String> SPREADSHEET_TYPES = Set.of(
            // Microsoft Excel
            "application/vnd.ms-excel",
            "application/vnd.ms-excel.sheet.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel.template.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            // OpenDocument spreadsheet
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.spreadsheet-template",
            // CSV
            "text/csv");

    String TEXT = "word";

    Set<String> TEXT_TYPES = Set.of(
            // Microsoft Word
            "application/msword",
            "application/vnd.ms-word.document.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-word.template.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            // OpenDocument text
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.text-template",
            // Rich text / plain text (opened as `word`)
            "application/rtf",
            "text/rtf",
            "text/plain");

    String PRESENTATION = "slide";

    Set<String> PRESENTATION_TYPES = Set.of(
            // Microsoft PowerPoint
            "application/vnd.ms-powerpoint",
            "application/vnd.ms-powerpoint.presentation.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
            "application/vnd.ms-powerpoint.template.macroenabled.12",
            "application/vnd.openxmlformats-officedocument.presentationml.template",
            "application/vnd.openxmlformats-officedocument.presentationml.slide",
            // OpenDocument presentation
            "application/vnd.oasis.opendocument.presentation",
            "application/vnd.oasis.opendocument.presentation-template");

}
