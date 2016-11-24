package com.github.bachelorpraktikum.dbvisualization.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

/**
 * Represents an element on the track.<p>Every element is associated with a {@link Node}.</p>
 * <p>Typically represents a signal. The {@link Type type} of signal can be retrieved via {@link
 * #getType()}</p>
 * <p>There is only one instance of Element per name per {@link Context}.</p>
 */
@ParametersAreNonnullByDefault
public final class Element {
    @Nonnull
    private final Factory factory;
    @Nonnull
    private final String name;
    @Nonnull
    private final Node node;
    @Nonnull
    private final Type type;
    @Nullable
    private final Switch aSwitch;
    @Nonnull
    private final ReadOnlyObjectWrapper<State> stateProperty;

    /**
     * Represents the state of an {@link Element}.
     */
    public enum State {
        NOSIG, STOP, FAHRT;

        /**
         * Gets the state with the given name.
         *
         * @param name the name
         * @return a state
         * @throws IllegalArgumentException if there is no state with that name
         */
        @Nonnull
        public static State fromName(String name) {
            switch (name) {
                case "NOSIG":
                    return NOSIG;
                case "STOP":
                    return STOP;
                case "FAHRT":
                    return FAHRT;
                default:
                    throw new IllegalArgumentException("unknown state");
            }
        }
    }

    /**
     * Represents the type of an {@link Element}.
     * Every type is associated with an image file containing the symbol for the element.
     */
    public enum Type {
        HauptSignalImpl,
        VorSignalImpl,
        SichtbarkeitsPunktImpl,
        GefahrenPunktImpl,
        MagnetImpl,
        WeichenPunktImpl,
        SwWechselImpl,
        GeschwindigkeitsAnzeigerImpl;

        private final String imageUrl;

        Type() {
            imageUrl = Element.class.getResource(String.format("symbols/%s.svg", name())).toExternalForm();
        }

        /**
         * Gets a potentially human readable name for this {@link Type}.
         *
         * @return the name of this type
         */
        @Nonnull
        public String getName() {
            return name();
        }

        /**
         * Gets a URL to an SVG image representing this {@link Type}.<br>
         * Typically, the image is contained in the application's jar file.
         *
         * @return the image url
         */
        @Nonnull
        public String getImageUrl() {
            return imageUrl;
        }

        /**
         * Gets the {@link Type} corresponding to the given type name.
         *
         * @param name the unique type name
         * @return a type
         * @throws IllegalArgumentException if no type with that name exists
         */
        @Nonnull
        public static Type fromName(String name) {
            // TODO change this. The name of the type should be given as an constructor argument.
            String[] nameParts = name.split("_");
            String typeName = nameParts[nameParts.length - 1];
            switch (typeName) {
                case "HauptSignalImpl":
                    return HauptSignalImpl;
                case "VorSignalImpl":
                    return VorSignalImpl;
                case "SichtbarkeitsPunktImpl":
                    return SichtbarkeitsPunktImpl;
                case "GefahrenPunktImpl":
                    return GefahrenPunktImpl;
                case "MagnetImpl":
                    return MagnetImpl;
                case "WeichenPunktImpl":
                    return WeichenPunktImpl;
                case "SwWechselImpl":
                    return SwWechselImpl;
                case "GeschwindigkeitsAnzeigerImpl":
                    return GeschwindigkeitsAnzeigerImpl;
                default:
                    throw new IllegalArgumentException("unknown type: " + name);
            }
        }
    }

    private Element(Factory factory, String name, Type type, Node node, State state) {
        this.factory = Objects.requireNonNull(factory);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.node = Objects.requireNonNull(node);
        this.stateProperty = new ReadOnlyObjectWrapper<>(Objects.requireNonNull(state));

        node.addElement(this);

        if (this.type == Type.WeichenPunktImpl) {
            this.aSwitch = factory.getSwitchFactory().create(this);
        } else {
            this.aSwitch = null;
        }

        // Add an event at time 0 with the initial state
        factory.addEvent(this, state, 0);
    }

    /**
     * <p>Manages all instances of {@link Element}. Ensures that is always only one instance of
     * element per name per {@link Context}.</p> <p>Additionally, provides the {@link #setTime(int)}
     * method to set the time of all Elements that exist in this factory's context.</p>
     */
    @ParametersAreNonnullByDefault
    public static final class Factory {
        private static final Map<Context, Factory> instances = new WeakHashMap<>();

        @Nonnull
        private final Map<String, Element> elements;

        @Nonnull
        private final Switch.Factory switchFactory;
        @Nonnull
        private final ObservableList<Event> unorderedEvents;
        @Nonnull
        private final SortedList<Event> events;
        private int currentTime;
        private int nextIndex;

        @Nonnull
        private static Factory getInstance(Context context) {
            if (context == null) {
                throw new NullPointerException("context is null");
            }
            return instances.computeIfAbsent(context, g -> new Factory(context));
        }

        private Factory(Context context) {
            this.elements = new HashMap<>(64);

            this.switchFactory = Switch.in(context);
            this.unorderedEvents = FXCollections.observableArrayList();
            this.events = new SortedList<>(unorderedEvents);
            this.currentTime = -1;
            this.nextIndex = 0;
        }

        @Nonnull
        private Switch.Factory getSwitchFactory() {
            return switchFactory;
        }

        /**
         * Potentially creates a new instance of {@link Element}.<br>
         * If an element with the same name already exists, it is returned.
         *
         * @param name  the unique name of this element
         * @param type  the {@link Type}
         * @param node  the {@link Node} this element is located on
         * @param state the initial state of the element
         * @return an element
         * @throws NullPointerException     if either of the arguments is null
         * @throws IllegalArgumentException if an element with this name already exists, but with
         *                                  different arguments
         */
        @Nonnull
        public Element create(String name, Type type, Node node, State state) {
            Element element = elements.computeIfAbsent(Objects.requireNonNull(name), elementName ->
                    new Element(this, elementName, type, node, state)
            );
            if (!element.getName().equals(name)
                    || !element.getType().equals(type)
                    || !element.getNode().equals(node)) {
                // we SHOULD also check for the initial state, but it's not easily accessible,
                // so I'm going to let it slide for now
                String errorMessage = String.format("element with this name (%s) already exists, but differently", name);
                throw new IllegalArgumentException(errorMessage);
            }
            return element;
        }

        /**
         * Gets the {@link Element} with the given unique name.
         *
         * @param name the element's name
         * @return the element instance with this name
         * @throws NullPointerException     if the name is null
         * @throws IllegalArgumentException if there is no element associated with the name
         */
        @Nonnull
        public Element get(String name) {
            Element element = elements.get(Objects.requireNonNull(name));
            if (element == null) {
                throw new IllegalArgumentException("unknown element: " + name);
            }
            return element;
        }

        /**
         * Gets all {@link Element} instances in this {@link Context}.
         *
         * @return all elements
         */
        @Nonnull
        public Collection<Element> getAll() {
            return Collections.unmodifiableCollection(elements.values());
        }

        private void addEvent(Element element, State state, int time) {
            if (time < 0) {
                throw new IllegalArgumentException("time is negative");
            }
            if (!events.isEmpty() && time < events.get(events.size() - 1).getTime()) {
                throw new IllegalStateException("tried to add event before last event");
            }
            unorderedEvents.add(new Event(element, time, state));
            // maybe the states has to be updated
            if (time <= currentTime) {
                int refreshTime = currentTime;
                resetTime();
                setTime(refreshTime);
            }
        }

        private void resetTime() {
            currentTime = -1;
            nextIndex = 0;
        }

        /**
         * Changes the time for all {@link Element elements} in this context.
         *
         * @param time the time in milliseconds
         * @throws IllegalArgumentException if time is negative
         */
        public void setTime(int time) {
            if (time < 0) {
                throw new IllegalArgumentException("negative time: " + time);
            }

            if (time == currentTime) {
                return;
            }

            if (time < currentTime) {
                resetTime();
            }

            Event event;
            while (nextIndex < events.size() && (event = events.get(nextIndex)).getTime() <= time) {
                currentTime = event.getTime();
                nextIndex += 1;
                event.fire();
            }
        }
    }

    /**
     * Gets the {@link Factory} instance for the given {@link Context}.
     *
     * @param context the context
     * @return the factory
     * @throws NullPointerException if context is null
     */
    @Nonnull
    public static Factory in(Context context) {
        return Element.Factory.getInstance(context);
    }

    @Nonnull
    private Factory getFactory() {
        return this.factory;
    }

    /**
     * Adds an event for this {@link Element}.
     *
     * @param state new state after this event
     * @param time  the time of the event in milliseconds
     * @throws NullPointerException     if state is null
     * @throws IllegalStateException    if there is already another event after this one
     * @throws IllegalArgumentException if time is negative
     */
    public void addEvent(State state, int time) {
        getFactory().addEvent(this, state, time);
    }

    /**
     * Gets the unique name of this {@link Element}.
     *
     * @return the name
     */
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * Gets the {@link Node} this element is located at.
     *
     * @return the node
     */
    @Nonnull
    public Node getNode() {
        return node;
    }

    /**
     * Gets the property representing the {@link State} of this {@link Element}.<br>
     * The state will change, if {@link Factory#setTime(int)} is called.
     *
     * @return the state property
     */
    @Nonnull
    public ReadOnlyProperty<State> stateProperty() {
        return stateProperty.getReadOnlyProperty();
    }

    /**
     * Gets the current {@link State} of this {@link Element}.<br> Note that the returned state is
     * immutable and therefore will not change if the state of this element changes. To track the
     * state of this element, use the {@link ReadOnlyProperty} returned by {@link #stateProperty()}.
     *
     * @return the current state
     */
    @Nonnull
    public State getState() {
        return stateProperty.getValue();
    }

    /**
     * Gets the {@link Type} of this {@link Element}.
     *
     * @return the type
     */
    @Nonnull
    public Type getType() {
        return type;
    }

    /**
     * Gets the switch this {@link Element} is part of.<br>There will only be a value present, if
     * the type of this element is {@link Type#WeichenPunktImpl}.
     *
     * @return the switch this element is part of
     */
    @Nonnull
    public Optional<Switch> getSwitch() {
        return Optional.ofNullable(aSwitch);
    }

    @Override
    public String toString() {
        return "Element{"
                + "name='" + name + '\''
                + ", state=" + stateProperty.getValue()
                + '}';
    }

    @ParametersAreNonnullByDefault
    private static class Event implements Comparable<Event> {
        @Nonnull
        private final Element element;
        private final int time;
        @Nonnull
        private final State state;

        private Event(Element element, int time, State state) {
            this.element = element;
            this.time = time;
            this.state = state;
        }

        @Nonnull
        public Element getElement() {
            return element;
        }

        public int getTime() {
            return time;
        }

        @Nonnull
        public State getState() {
            return state;
        }

        private void fire() {
            element.stateProperty.setValue(state);
        }

        @Override
        public int compareTo(Event other) {
            return Integer.compare(time, other.getTime());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            Event event = (Event) obj;

            if (time != event.time) return false;
            return state == event.state;
        }

        @Override
        public int hashCode() {
            int result = time;
            result = 31 * result + state.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Event{"
                    + "time=" + time
                    + ", state=" + state
                    + '}';
        }
    }
}