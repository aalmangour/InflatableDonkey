/*
 * The MIT License
 *
 * Copyright 2016 Ahseya.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.horrorho.inflatabledonkey.cloud.clients;

import com.github.horrorho.inflatabledonkey.cloudkitty.CloudKitty;
import com.github.horrorho.inflatabledonkey.cloudkitty.operations.RecordRetrieveRequestOperations;
import com.github.horrorho.inflatabledonkey.data.backup.KeyBag;
import com.github.horrorho.inflatabledonkey.data.backup.KeyBagFactory;
import com.github.horrorho.inflatabledonkey.data.backup.KeyBagID;
import com.github.horrorho.inflatabledonkey.pcs.zone.PZFactory;
import com.github.horrorho.inflatabledonkey.pcs.zone.ProtectionZone;
import com.github.horrorho.inflatabledonkey.protobuf.CloudKit;
import com.google.protobuf.ByteString;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import net.jcip.annotations.Immutable;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Ahseya
 */
@Immutable
public final class KeyBagClient {

    private static final Logger logger = LoggerFactory.getLogger(KeyBagClient.class);

    public static Optional<KeyBag>
            keyBag(HttpClient httpClient, CloudKitty kitty, ProtectionZone zone, KeyBagID keyBagID)
            throws UncheckedIOException {
        logger.debug("-- keyBag() - keybag UUID: {}", keyBagID);

        List<CloudKit.RecordRetrieveResponse> responses
                = RecordRetrieveRequestOperations.get(kitty, httpClient, "mbksync", keyBagID.toString());
        if (responses.size() != 1) {
            logger.warn("-- keyBag() - bad response list size: {}", responses);
            return Optional.empty();
        }

        CloudKit.ProtectionInfo protectionInfo = responses.get(0)
                .getRecord()
                .getProtectionInfo();

        Optional<ProtectionZone> optionalNewZone = PZFactory.instance().create(zone, protectionInfo);
        if (!optionalNewZone.isPresent()) {
            logger.warn("-- keyBag() - failed to retrieve protection info");
            return Optional.empty();
        }
        ProtectionZone newZone = optionalNewZone.get();

        return keyBag(responses.get(0), newZone);
    }

    static Optional<KeyBag> keyBag(CloudKit.RecordRetrieveResponse response, ProtectionZone zone) {
        Optional<byte[]> keyBagData = field(response.getRecord(), "keybagData", zone);
        if (!keyBagData.isPresent()) {
            logger.warn("-- keyBag() - failed to acquire key bag");
            return Optional.empty();
        }

        Optional<byte[]> secret = field(response.getRecord(), "secret", zone);
        if (!secret.isPresent()) {
            logger.warn("-- keyBag() - failed to acquire key bag pass code");
            return Optional.empty();
        }

        KeyBag keyBag = KeyBagFactory.create(keyBagData.get(), secret.get());
        return Optional.of(keyBag);
    }

    static Optional<byte[]> field(CloudKit.Record record, String label, ProtectionZone zone) {
        return record.getRecordFieldList()
                .stream()
                .filter(u -> u
                        .getIdentifier()
                        .getName()
                        .equals(label))
                .map(u -> u
                        .getValue()
                        .getBytesValue())
                .map(ByteString::toByteArray)
                .map(bs -> zone.decrypt(bs, label))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
