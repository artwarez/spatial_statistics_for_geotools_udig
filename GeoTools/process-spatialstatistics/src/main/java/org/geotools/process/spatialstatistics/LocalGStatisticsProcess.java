/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2014, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.process.spatialstatistics;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.process.Process;
import org.geotools.process.ProcessException;
import org.geotools.process.ProcessFactory;
import org.geotools.process.spatialstatistics.autocorrelation.LocalGStatisticOperation;
import org.geotools.process.spatialstatistics.core.FeatureTypes;
import org.geotools.process.spatialstatistics.core.Params;
import org.geotools.process.spatialstatistics.enumeration.DistanceMethod;
import org.geotools.process.spatialstatistics.enumeration.SpatialConcept;
import org.geotools.process.spatialstatistics.enumeration.StandardizationMethod;
import org.geotools.text.Text;
import org.geotools.util.NullProgressListener;
import org.geotools.util.logging.Logging;
import org.opengis.util.ProgressListener;

/**
 * Given a set of weighted features, identifies statistically significant hot spots and cold spots using the Getis-Ord Gi* statistic.
 * 
 * @author Minpa Lee, MangoSystem
 * 
 * @source $URL$
 */
public class LocalGStatisticsProcess extends AbstractStatisticsProcess {
    protected static final Logger LOGGER = Logging.getLogger(LocalGStatisticsProcess.class);

    private boolean started = false;

    public LocalGStatisticsProcess(ProcessFactory factory) {
        super(factory);
    }

    public ProcessFactory getFactory() {
        return factory;
    }

    public static SimpleFeatureCollection process(SimpleFeatureCollection inputFeatures,
            String inputField, SpatialConcept spatialConcept, DistanceMethod distanceMethod,
            StandardizationMethod standardization, Double searchDistance, ProgressListener monitor) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(LocalGStatisticsProcessFactory.inputFeatures.key, inputFeatures);
        map.put(LocalGStatisticsProcessFactory.inputField.key, inputField);
        map.put(LocalGStatisticsProcessFactory.spatialConcept.key, spatialConcept);
        map.put(LocalGStatisticsProcessFactory.distanceMethod.key, distanceMethod);
        map.put(LocalGStatisticsProcessFactory.standardization.key, standardization);
        map.put(LocalGStatisticsProcessFactory.searchDistance.key, searchDistance);

        Process process = new LocalGStatisticsProcess(null);
        Map<String, Object> resultMap;
        try {
            resultMap = process.execute(map, monitor);
            return (SimpleFeatureCollection) resultMap
                    .get(LocalGStatisticsProcessFactory.RESULT.key);
        } catch (ProcessException e) {
            LOGGER.log(Level.FINER, e.getMessage(), e);
        }

        return null;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> input, ProgressListener monitor)
            throws ProcessException {
        if (started)
            throw new IllegalStateException("Process can only be run once");
        started = true;

        if (monitor == null)
            monitor = new NullProgressListener();
        try {
            monitor.started();
            monitor.setTask(Text.text("Grabbing arguments"));
            monitor.progress(10.0f);

            SimpleFeatureCollection inputFeatures = (SimpleFeatureCollection) Params.getValue(
                    input, LocalGStatisticsProcessFactory.inputFeatures, null);
            String inputField = (String) Params.getValue(input,
                    LocalGStatisticsProcessFactory.inputField, null);
            if (inputFeatures == null || inputField == null) {
                throw new NullPointerException("inputFeatures and inputField parameters required");
            }

            inputField = FeatureTypes.validateProperty(inputFeatures.getSchema(), inputField);
            if (inputFeatures.getSchema().indexOf(inputField) == -1) {
                throw new NullPointerException(inputField + " field does not exist!");
            }

            SpatialConcept spatialConcept = (SpatialConcept) Params.getValue(input,
                    LocalGStatisticsProcessFactory.spatialConcept,
                    LocalGStatisticsProcessFactory.spatialConcept.sample);

            DistanceMethod distanceMethod = (DistanceMethod) Params.getValue(input,
                    LocalGStatisticsProcessFactory.distanceMethod,
                    LocalGStatisticsProcessFactory.distanceMethod.sample);

            StandardizationMethod standardization = (StandardizationMethod) Params.getValue(input,
                    LocalGStatisticsProcessFactory.standardization,
                    LocalGStatisticsProcessFactory.standardization.sample);

            Double searchDistance = (Double) Params.getValue(input,
                    LocalGStatisticsProcessFactory.searchDistance,
                    LocalGStatisticsProcessFactory.searchDistance.sample);

            monitor.setTask(Text.text("Processing ..."));
            monitor.progress(25.0f);

            if (monitor.isCanceled()) {
                return null; // user has canceled this operation
            }

            // start process
            SimpleFeatureCollection resultFc = null;
            try {
                LocalGStatisticOperation process = new LocalGStatisticOperation();
                process.setSpatialConceptType(spatialConcept);
                process.setDistanceType(distanceMethod);
                process.setStandardizationType(standardization);

                // searchDistance
                if (searchDistance > 0 && !Double.isNaN(searchDistance)) {
                    process.setDistanceBand(searchDistance);
                }

                resultFc = process.execute(inputFeatures, inputField);
            } catch (Exception ee) {
                monitor.exceptionOccurred(ee);
            }
            // end process

            monitor.setTask(Text.text("Encoding result"));
            monitor.progress(90.0f);

            Map<String, Object> resultMap = new HashMap<String, Object>();
            resultMap.put(LocalGStatisticsProcessFactory.RESULT.key, resultFc);
            monitor.complete(); // same as 100.0f

            return resultMap;
        } catch (Exception eek) {
            monitor.exceptionOccurred(eek);
            return null;
        } finally {
            monitor.dispose();
        }
    }

}
