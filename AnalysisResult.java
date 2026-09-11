package com.mahzooz.monitor;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {
    public final long timestamp;
    public final List<Detector.Marker> green;
    public final List<Detector.Marker> yellow;
    public final List<Detector.Tile> tiles;
    public final List<String> relations;
    public final String recommendation;
    public final String observedChange;
    public final float confidence;
    public final String coordinateSummary;

    public AnalysisResult(long timestamp,
                          List<Detector.Marker> green,
                          List<Detector.Marker> yellow,
                          List<Detector.Tile> tiles,
                          List<String> relations,
                          String recommendation,
                          String observedChange,
                          float confidence,
                          String coordinateSummary) {
        this.timestamp = timestamp;
        this.green = green == null ? new ArrayList<>() : green;
        this.yellow = yellow == null ? new ArrayList<>() : yellow;
        this.tiles = tiles == null ? new ArrayList<>() : tiles;
        this.relations = relations == null ? new ArrayList<>() : relations;
        this.recommendation = recommendation == null ? "" : recommendation;
        this.observedChange = observedChange == null ? "" : observedChange;
        this.confidence = confidence;
        this.coordinateSummary = coordinateSummary == null ? "" : coordinateSummary;
    }
}
