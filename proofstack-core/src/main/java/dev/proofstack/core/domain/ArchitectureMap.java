package dev.proofstack.core.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Projects a {@link Profile} onto areas: the "system architecture" view of a career.
 * Only areas with content are included, in {@link Area} declaration order.
 */
public record ArchitectureMap(List<AreaNode> nodes) {

    public ArchitectureMap {
        nodes = Checks.list(nodes, "nodes");
    }

    public static ArchitectureMap from(Profile profile) {
        List<AreaNode> nodes = new ArrayList<>();
        for (Area area : Area.values()) {
            List<Lab> labs = profile.labs().stream()
                    .filter(lab -> lab.type().area().filter(area::equals).isPresent())
                    .toList();

            // A project belongs to an area if it declares it, or if it is part of a lab of that area.
            Set<Project> projects = new LinkedHashSet<>();
            profile.projects().stream().filter(p -> p.areas().contains(area)).forEach(projects::add);
            labs.stream()
                    .flatMap(lab -> lab.projectIds().stream())
                    .map(profile::project)
                    .flatMap(java.util.Optional::stream)
                    .forEach(projects::add);

            AreaNode node = new AreaNode(
                    area,
                    profile.skills().stream().filter(s -> s.area() == area).toList(),
                    List.copyOf(projects),
                    profile.evidence().stream().filter(e -> e.areas().contains(area)).toList(),
                    labs);
            if (!node.isEmpty()) {
                nodes.add(node);
            }
        }
        return new ArchitectureMap(nodes);
    }
}
