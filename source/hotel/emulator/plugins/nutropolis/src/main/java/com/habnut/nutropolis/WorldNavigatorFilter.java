package com.habnut.nutropolis;

import com.eu.habbo.habbohotel.navigation.NavigatorFilter;
import com.eu.habbo.habbohotel.navigation.NavigatorFilterField;
import com.eu.habbo.habbohotel.navigation.SearchResultList;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps one of the navigator's views so each world sees only its own rooms:
 * no city places in the hotel's navigator, no hotel rooms in the city's. The
 * room lists are copied, never edited: they belong to the navigator.
 */
final class WorldNavigatorFilter extends NavigatorFilter {
    private final NavigatorFilter inner;
    private final City city;

    WorldNavigatorFilter(NavigatorFilter inner, City city) {
        super(inner.viewName);
        this.inner = inner;
        this.city = city;
    }

    @Override
    public List<SearchResultList> getResult(Habbo habbo) {
        return this.only(habbo, this.inner.getResult(habbo));
    }

    @Override
    public List<SearchResultList> getResult(Habbo habbo, NavigatorFilterField field, String value, int roomCategory) {
        return this.only(habbo, this.inner.getResult(habbo, field, value, roomCategory));
    }

    private List<SearchResultList> only(Habbo habbo, List<SearchResultList> lists) {
        if (habbo == null || lists == null) return lists;
        boolean cityWorld = this.city.inCityWorld(habbo);
        List<SearchResultList> out = new ArrayList<>(lists.size());
        for (SearchResultList list : lists) {
            List<Room> rooms;
            synchronized (list.rooms) {
                rooms = new ArrayList<>(list.rooms);
            }
            int before = rooms.size();
            rooms.removeIf(room -> room != null && this.city.inCity(room) != cityWorld);
            if (before > 0 && rooms.isEmpty()) continue; // a section that was all the other world's
            out.add(new SearchResultList(list.order, list.code, list.query, list.action, list.mode, list.hidden, rooms,
                    list.filter, list.showInvisible, list.displayOrder, list.categoryOrder));
        }
        return out;
    }
}
