package com.mygame.texture;

import com.mygame.texture.TextureDefinition.Layer;
import com.mygame.texture.TextureDefinition.LayerType;
import com.mygame.texture.TextureDefinition.MapSpec;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Parses the procedural texture catalog and enforces the rules documented in {@code textures.xml}. */
final class TextureDefinitionLoader {
    private TextureDefinitionLoader() {}

    /**
     * Reads unique texture definitions from an XML stream.
     *
     * @param in XML source stream
     * @param source label included in diagnostics
     * @return definitions keyed by their declared names, preserving document order
     * @throws IllegalStateException if parsing or validation fails
     */
    static Map<String, TextureDefinition> load(InputStream in, String source) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document doc = factory.newDocumentBuilder().parse(in);
            Element root = doc.getDocumentElement();
            if (!"textures".equals(root.getTagName())) {
                throw new IllegalArgumentException("Expected XML root: textures");
            }
            Map<String, TextureDefinition> result = new LinkedHashMap<>();
            for (Element t : children(root, "texture")) {
                TextureDefinition def = parseTexture(t);
                if (result.put(def.name, def) != null) {
                    throw new IllegalArgumentException("Duplicate texture: " + def.name);
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read " + source + ": " + e.getMessage(), e);
        }
    }

    private static TextureDefinition parseTexture(Element t) {
        String name = req(t, "name");
        int size = (int) num(t, "size", 256f);
        if (size < 16 || size > 1024 || (size & (size - 1)) != 0) {
            throw new IllegalArgumentException(name + ".size must be a power of two between 16 and 1024");
        }
        float tile = num(t, "tileMeters", 4f);
        if (tile <= 0f) throw new IllegalArgumentException(name + ".tileMeters must be > 0");

        MapSpec diffuse = parseMap(first(t, "diffuse"), name, false);
        MapSpec normal = parseMap(first(t, "normal"), name, true);
        if (diffuse == null && normal == null) {
            throw new IllegalArgumentException(name + " must define either <diffuse> or <normal>");
        }
        return new TextureDefinition(name, size, tile, diffuse, normal);
    }

    private static MapSpec parseMap(Element e, String texName, boolean normal) {
        if (e == null) return null;
        List<Layer> layers = new ArrayList<>();
        for (Element l : children(e, "layer")) layers.add(parseLayer(l, texName));
        if (layers.isEmpty()) throw new IllegalArgumentException(texName + " must contain at least one <layer>");
        return new MapSpec(layers,
                num(e, "base", 0.9f), num(e, "contrast", 1f), num(e, "strength", 6f),
                num(e, "r", 1f), num(e, "g", 1f), num(e, "b", 1f));
    }

    private static Layer parseLayer(Element l, String texName) {
        LayerType type;
        try {
            type = LayerType.valueOf(req(l, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(texName + " has unknown layer type '" + l.getAttribute("type") + "'");
        }
        float amp = num(l, "amplitude", 1f);
        int cells = (int) num(l, "cells", 8f);
        int octaves = (int) num(l, "octaves", 1f);
        float persistence = num(l, "persistence", 0.5f);
        int frequency = (int) num(l, "frequency", 4f);
        int warpCells = (int) num(l, "warpCells", 4f);
        float warpAmount = num(l, "warpAmount", 0f);

        if (type == LayerType.PERLIN) {
            if (cells < 1 || octaves < 1 || octaves > 8 || ((long) cells << (octaves - 1)) > 256) {
                throw new IllegalArgumentException(texName + " : perlin requires cells>=1, 1<=octaves<=8, and cells*2^(octaves-1) <= 256");
            }
        } else if (type == LayerType.RIPPLE) {
            if (frequency < 1 || warpCells < 1 || warpCells > 256) {
                throw new IllegalArgumentException(texName + " : ripple requires frequency>=1 and 1<=warpCells<=256");
            }
        }
        return new Layer(type, amp, cells, octaves, persistence, frequency, warpCells, warpAmount);
    }

    // ---- utilitaires XML ----
    private static List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && name.equals(((Element) n).getTagName())) out.add((Element) n);
        }
        return out;
    }

    private static Element first(Element parent, String name) {
        List<Element> c = children(parent, name);
        return c.isEmpty() ? null : c.getFirst();
    }

    private static String req(Element e, String attr) {
        String v = e.getAttribute(attr);
        if (v.isEmpty()) throw new IllegalArgumentException(e.getTagName() + "." + attr + " is required");
        return v;
    }

    private static float num(Element e, String attr, float def) {
        if (!e.hasAttribute(attr)) return def;
        float v = Float.parseFloat(e.getAttribute(attr).trim());
        if (!Float.isFinite(v)) throw new IllegalArgumentException(e.getTagName() + "." + attr + " must be finite");
        return v;
    }
}