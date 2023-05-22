package subway.domain;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import subway.application.exception.SubwayInternalServerException;
import subway.application.exception.SubwayServiceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Subway {

    private static final String INVALID_ALREADY_EXISTS_TWO_STATIONS_MESSAGE = "노선에 이미 존재하는 두 역을 등록할 수 없습니다.";
    private static final String INVALID_NOT_EXISTS_TWO_STATIONS_MESSAGE = "존재하지 않는 역들과의 구간을 등록할 수 없습니다.";
    private static final String INVALID_NOT_FOUND_RIGHT_SIDE_SECTION_MESSAGE = "오른쪽 구간을 찾을 수 없습니다.";
    private static final String INVALID_NOT_FOUND_LEFT_SIDE_SECTION_MESSAGE = "왼쪽 구간을 찾을 수 없습니다.";
    private static final String INVALID_DISTANCE_BETWEEN_BASE_AND_NEW_STATION_MESSAGE = "기존 역 사이 길이보다 크거나 같은 길이의 구간을 등록할 수 없습니다.";
    private static final String INVALID_NOT_FOUND_STATION_MESSAGE = "역이 노선에 없습니다.";

    private final Line line;
    private final SimpleDirectedWeightedGraph<Station, DefaultWeightedEdge> subwayStructure;
    private final Station start;

    private Subway(final Line line, final SimpleDirectedWeightedGraph<Station, DefaultWeightedEdge> subwayStructure,
                   final Station start) {
        this.line = line;
        this.subwayStructure = subwayStructure;
        this.start = start;
    }

    public static Subway of(final Line line, final List<Section> sections) {
        SimpleDirectedWeightedGraph<Station, DefaultWeightedEdge> subwayStructure = generateSubwayStructure(sections);
        Station start = findStartStation(subwayStructure);
        return new Subway(line, subwayStructure, start);
    }

    private static SimpleDirectedWeightedGraph<Station, DefaultWeightedEdge> generateSubwayStructure(final List<Section> sections) {
        SimpleDirectedWeightedGraph<Station, DefaultWeightedEdge> subwayStructure = new SimpleDirectedWeightedGraph<>(
                DefaultWeightedEdge.class);
        for (Section section : sections) {
            Station right = section.getRight();
            Station left = section.getLeft();

            subwayStructure.addVertex(right);
            subwayStructure.addVertex(left);
            subwayStructure.setEdgeWeight(subwayStructure.addEdge(left, right), section.getDistance());
        }
        return subwayStructure;
    }

    private static Station findStartStation(final SimpleDirectedWeightedGraph<Station, DefaultWeightedEdge> subwayStructure) {
        return subwayStructure.vertexSet()
                .stream()
                .filter(station -> isStartStation(subwayStructure, station))
                .findFirst()
                .orElse(null);
    }

    private static boolean isStartStation(final SimpleDirectedWeightedGraph<Station, DefaultWeightedEdge> subwayStructure, final Station station) {
        return subwayStructure.incomingEdgesOf(station).isEmpty();
    }

    public Sections findUpdateSectionsByAddingSection(final Section section) {
        Station left = section.getLeft();
        Station right = section.getRight();
        int distance = section.getDistance();

        validateStation(left, right);
        return getSections(left, right, distance);
    }

    private void validateStation(final Station left, final Station right) {
        if (hasStation(left) && hasStation(right)) {
            throw new SubwayServiceException(INVALID_ALREADY_EXISTS_TWO_STATIONS_MESSAGE);
        }
        if (hasNoStation(left) && hasNoStation(right) && isStationsPresent()) {
            throw new SubwayServiceException(INVALID_NOT_EXISTS_TWO_STATIONS_MESSAGE);
        }
    }

    private boolean hasStation(final Station station) {
        return subwayStructure.containsVertex(station);
    }

    private boolean hasNoStation(final Station station) {
        return !hasStation(station);
    }

    private boolean isStationsPresent() {
        return !isStationsEmpty();
    }

    private boolean isStationsEmpty() {
        return subwayStructure.edgeSet().isEmpty();
    }

    private Sections getSections(final Station left, final Station right, final int distance) {
        if (hasStation(left) && hasNoStation(right)) {
            return getLeftSectionsInBetween(left, right, distance);
        }
        if (hasNoStation(left) && hasStation(right)) {
            return getRightSectionsInBetween(left, right, distance);
        }
        return new Sections(List.of(new Section(line, left, right, new Distance(distance))));
    }

    private Sections getLeftSectionsInBetween(final Station left, final Station right, final int distance) {
        Section newSection = new Section(line, left, right, new Distance(distance));
        if (hasRightSection(left)) {
            Section existedSection = findRightSection(left);
            Section updatedRightSection = getUpdatedRightSection(existedSection, right, distance);
            return new Sections(List.of(newSection, updatedRightSection));
        }
        return new Sections(List.of(newSection));
    }

    private boolean hasRightSection(final Station station) {
        if (hasNoStation(station)) {
            throw new SubwayServiceException(INVALID_NOT_FOUND_STATION_MESSAGE);
        }
        return !isEndStation(station);
    }

    private boolean isEndStation(final Station station) {
        return subwayStructure.outgoingEdgesOf(station).isEmpty();
    }

    private Section findRightSection(final Station station) {
        Set<DefaultWeightedEdge> edges = getRightEdge(station);
        return edges.stream()
                .map(edge -> new Section(line, station, getRightStationByEdge(edge), new Distance(getDistance(edge))))
                .findFirst()
                .orElseThrow(() -> new SubwayInternalServerException(INVALID_NOT_FOUND_RIGHT_SIDE_SECTION_MESSAGE));
    }

    private Set<DefaultWeightedEdge> getRightEdge(Station station) {
        return subwayStructure.outgoingEdgesOf(station);
    }

    private Station getRightStationByEdge(DefaultWeightedEdge edge) {
        return subwayStructure.getEdgeTarget(edge);
    }

    private int getDistance(DefaultWeightedEdge edge) {
        return (int) subwayStructure.getEdgeWeight(edge);
    }

    private Section getUpdatedRightSection(final Section existedSection, final Station newRightStation, final int newDistance) {
        int existedDistance = existedSection.getDistance();
        validateNewDistance(newDistance, existedDistance);

        return new Section(line, newRightStation, existedSection.getRight(), new Distance(existedDistance - newDistance));
    }

    private void validateNewDistance(int newDistance, int existedDistance) {
        if (existedDistance <= newDistance) {
            throw new SubwayServiceException(INVALID_DISTANCE_BETWEEN_BASE_AND_NEW_STATION_MESSAGE);
        }
    }

    private Sections getRightSectionsInBetween(Station left, Station right, int distance) {
        Section newSection = new Section(line, left, right, new Distance(distance));
        if (hasLeftSection(right)) {
            Section existedSection = findLeftSection(right);
            Section updatedLeftSection = getUpdatedLeftSection(existedSection, left, distance);
            return new Sections(List.of(newSection, updatedLeftSection));
        }
        return new Sections(List.of(newSection));
    }

    private boolean hasLeftSection(final Station station) {
        if (hasNoStation(station)) {
            throw new SubwayServiceException(INVALID_NOT_FOUND_STATION_MESSAGE);
        }
        return !isStartStation(station);
    }

    private boolean isStartStation(final Station station) {
        return subwayStructure.incomingEdgesOf(station).isEmpty();
    }

    private Section findLeftSection(final Station station) {
        Set<DefaultWeightedEdge> edges = getLeftEdge(station);
        return edges.stream()
                .map(edge -> new Section(line, getLeftStationByEdge(edge), station, new Distance(getDistance(edge))))
                .findFirst()
                .orElseThrow(() -> new SubwayInternalServerException(INVALID_NOT_FOUND_LEFT_SIDE_SECTION_MESSAGE));
    }

    private Set<DefaultWeightedEdge> getLeftEdge(Station station) {
        return subwayStructure.incomingEdgesOf(station);
    }

    private Station getLeftStationByEdge(DefaultWeightedEdge edge) {
        return subwayStructure.getEdgeSource(edge);
    }

    private Section getUpdatedLeftSection(Section existedSection, Station newLeftStation, int newDistance) {
        int existedDistance = existedSection.getDistance();
        validateNewDistance(newDistance, existedDistance);

        return new Section(line, existedSection.getLeft(), newLeftStation, new Distance(existedDistance - newDistance));
    }

    public Sections findUpdateSectionsByDeletingSection(Station station) {
        if (hasRightSection(station) && hasLeftSection(station)) {
            Section leftSection = findLeftSection(station);
            Section rightSection = findRightSection(station);

            Station left = leftSection.getLeft();
            Station right = rightSection.getRight();
            Distance distance = new Distance(leftSection.getDistance() + rightSection.getDistance());
            return new Sections(List.of(new Section(line, left, right, distance)));
        }
        return new Sections(new ArrayList<>());
    }

    public List<Station> getOrderedStations() {
        List<Station> orderedStations = new ArrayList<>();
        if (isStationsEmpty()) {
            return orderedStations;
        }
        Station station = new Station(start.getId(), start.getName());
        while (!isEndStation(station)) {
            orderedStations.add(station);
            station = findRightSection(station).getRight();
        }
        orderedStations.add(station);
        return orderedStations;
    }

    public Line getLine() {
        return line;
    }
}
