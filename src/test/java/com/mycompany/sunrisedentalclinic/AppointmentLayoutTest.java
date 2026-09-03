package com.mycompany.sunrisedentalclinic;

import java.io.InputStream;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentLayoutTest {

    private static final String FXML_NAMESPACE = "http://javafx.com/fxml";
    private static final String DASHBOARD_FXML
            = "/com/mycompany/sunrisedentalclinic/view/dashboard.fxml";

    @Test
    void appointmentFormFillsAvailableWidth() throws Exception {
        Document document;
        try (InputStream fxml = getClass().getResourceAsStream(DASHBOARD_FXML)) {
            assertNotNull(fxml, "dashboard.fxml must be available on the test classpath");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            document = factory.newDocumentBuilder().parse(fxml);
        }

        Element patientSearch = findByFxId(document, "aPatientSearch");
        assertNotNull(patientSearch, "The appointment patient search field must exist");

        Element appointmentGrid = nearestAncestor(patientSearch, "GridPane");
        assertNotNull(appointmentGrid, "The appointment fields must be inside a GridPane");
        assertEquals("Infinity", appointmentGrid.getAttribute("maxWidth"));

        Element constraints = directChild(appointmentGrid, "columnConstraints");
        assertNotNull(constraints, "The appointment GridPane must define responsive columns");
        NodeList columns = constraints.getElementsByTagName("ColumnConstraints");
        assertEquals(4, columns.getLength(), "The appointment form must have four columns");
        for (int index = 0; index < columns.getLength(); index++) {
            Element column = (Element) columns.item(index);
            assertEquals("25", column.getAttribute("percentWidth"));
            assertEquals("ALWAYS", column.getAttribute("hgrow"));
            assertEquals("0", column.getAttribute("minWidth"));
        }

        Set<String> growingControls = Set.of(
                "aPatientSearch", "aPatientName", "aAddress", "aContact", "aEmail",
                "aGender", "aDentist", "aTreatment", "aDate", "aTime", "aNotes");
        for (String fxId : growingControls) {
            Element control = findByFxId(document, fxId);
            assertNotNull(control, fxId + " must exist");
            assertEquals("Infinity", control.getAttribute("maxWidth"),
                    fxId + " must grow to fill its grid cell");
        }

        Element registerButton = directChildByText(appointmentGrid, "Register Appointment");
        assertNotNull(registerButton, "The register appointment button must exist");
        assertEquals("Infinity", registerButton.getAttribute("maxWidth"));
    }

    private Element findByFxId(Document document, String fxId) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (fxId.equals(element.getAttributeNS(FXML_NAMESPACE, "id"))) {
                return element;
            }
        }
        return null;
    }

    private Element nearestAncestor(Element element, String tagName) {
        Node current = element.getParentNode();
        while (current instanceof Element ancestor) {
            if (tagName.equals(ancestor.getTagName())) {
                return ancestor;
            }
            current = current.getParentNode();
        }
        return null;
    }

    private Element directChild(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private Element directChildByText(Element parent, String text) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && text.equals(element.getAttribute("text"))) {
                return element;
            }
        }
        return null;
    }
}
