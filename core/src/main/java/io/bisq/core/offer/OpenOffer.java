/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.offer;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Version;
import io.bisq.common.storage.Storage;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.TradableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Date;

@Slf4j
public final class OpenOffer implements Tradable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    // Timeout for offer reservation during takeoffer process. If deposit tx is not completed in that time we reset the offer to AVAILABLE state.
    private static final long TIMEOUT_SEC = 30;
    transient private Timer timeoutTimer;

    public enum State {
        AVAILABLE,
        RESERVED,
        CLOSED,
        CANCELED
    }

    @Getter
    private final Offer offer;
    @Getter
    private State state = State.AVAILABLE;

    transient private Storage<TradableList<OpenOffer>> storage;

    public OpenOffer(Offer offer, Storage<TradableList<OpenOffer>> storage) {
        this.offer = offer;
        this.storage = storage;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();

            // If we have a reserved state from the local db we reset it
            if (state == State.RESERVED)
                setState(State.AVAILABLE);

        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    public Date getDate() {
        return offer.getDate();
    }

    @Override
    public String getId() {
        return offer.getId();
    }

    @Override
    public String getShortId() {
        return offer.getShortId();
    }

    public void setStorage(Storage<TradableList<OpenOffer>> storage) {
        this.storage = storage;
    }

    public void setState(State state) {
        log.trace("setState" + state);
        boolean changed = this.state != state;
        this.state = state;
        if (changed)
            storage.queueUpForSave();

        // We keep it reserved for a limited time, if trade preparation fails we revert to available state
        if (this.state == State.RESERVED)
            startTimeout();
        else
            stopTimeout();
    }

    private void startTimeout() {
        stopTimeout();

        timeoutTimer = UserThread.runAfter(() -> {
            log.debug("Timeout for resettin State.RESERVED reached");
            if (state == State.RESERVED)
                setState(State.AVAILABLE);
        }, TIMEOUT_SEC);
    }

    private void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OpenOffer openOffer = (OpenOffer) o;

        if (offer != null ? !offer.equals(openOffer.offer) : openOffer.offer != null) return false;
        return state == openOffer.state;

    }

    @Override
    public int hashCode() {
        int result = offer != null ? offer.hashCode() : 0;
        result = 31 * result + (state != null ? state.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "OpenOffer{" +
                "\n\toffer=" + offer +
                "\n\tstate=" + state +
                '}';
    }
}

