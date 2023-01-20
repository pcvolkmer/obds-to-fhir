package org.miracum.streams.ume.onkoadttofhir.processor;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Reference;
import org.miracum.streams.ume.onkoadttofhir.FhirProperties;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExport;
import org.miracum.streams.ume.onkoadttofhir.model.MeldungExportList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class OnkoProcessor {
  protected final FhirProperties fhirProperties;

  private static final Logger log = LoggerFactory.getLogger(OnkoProcessor.class);

  protected OnkoProcessor(final FhirProperties fhirProperties) {
    this.fhirProperties = fhirProperties;
  }

  protected String getHash(String type, String id) {
    String idToHash;
    switch (type) {
      case "Patient":
        idToHash = fhirProperties.getSystems().getPatientId();
        break;
      case "Condition":
        idToHash = fhirProperties.getSystems().getConditionId();
        break;
      case "Observation":
        idToHash = fhirProperties.getSystems().getObservationId();
        break;
      case "Surrogate":
        return Hashing.sha256().hashString(id, StandardCharsets.UTF_8).toString();
      default:
        return null;
    }
    return Hashing.sha256().hashString(idToHash + "|" + id, StandardCharsets.UTF_8).toString();
  }

  protected static String convertId(String id) {
    Pattern pattern = Pattern.compile("[^0]\\d{8}");
    Matcher matcher = pattern.matcher(id);
    var convertedId = "";
    if (matcher.find()) {
      convertedId = matcher.group();
    } else {
      log.warn("Identifier to convert does not have 9 digits without leading '0': " + id);
    }
    return convertedId;
  }

  public List<MeldungExport> prioritiseLatestMeldungExports(
      MeldungExportList meldungExports, List<String> priorityOrder) {
    var meldungen = meldungExports.getElements();

    var meldungExportMap = new HashMap<Integer, MeldungExport>();
    // meldeanlass bleibt in LKR Meldung immer gleich
    for (var meldung : meldungen) {
      var lkrId = meldung.getLkr_meldung();
      var currentMeldungVersion = meldungExportMap.get(lkrId);
      if (currentMeldungVersion == null
          || meldung.getVersionsnummer() > currentMeldungVersion.getVersionsnummer()) {
        meldungExportMap.put(lkrId, meldung);
      }
    }

    Collections.reverse(priorityOrder);

    Comparator<MeldungExport> meldungComparator =
        Comparator.comparing(
            m ->
                priorityOrder.indexOf(
                    m.getXml_daten()
                        .getMenge_Patient()
                        .getPatient()
                        .getMenge_Meldung()
                        .getMeldung()
                        .getMeldeanlass()));

    List<MeldungExport> meldungExportList = new ArrayList<>(meldungExportMap.values());
    meldungExportList.sort(meldungComparator);

    return meldungExportList;
  }

  public String getTumorIdFromAdt(MeldungExport meldung) {
    return meldung
        .getXml_daten()
        .getMenge_Patient()
        .getPatient()
        .getMenge_Meldung()
        .getMeldung()
        .getTumorzuordnung()
        .getTumor_ID();
  }

  protected Bundle addResourceAsEntryInBundle(Bundle bundle, DomainResource resource) {
    bundle
        .addEntry()
        .setFullUrl(
            new Reference(
                    String.format("%s/%s", resource.getResourceType().name(), resource.getId()))
                .getReference())
        .setResource(resource)
        .setRequest(
            new Bundle.BundleEntryRequestComponent()
                .setMethod(Bundle.HTTPVerb.PUT)
                .setUrl(
                    String.format("%s/%s", resource.getResourceType().name(), resource.getId())));

    return bundle;
  }
}