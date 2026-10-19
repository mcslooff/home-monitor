package nl.tufa.poc.bme680.services;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.tufa.poc.bme680.controller.dto.Bme680MeasurementDto;
import nl.tufa.poc.bme680.model.Bme680Measurement;

@Slf4j
@Service
@RequiredArgsConstructor
public class Bme680Service {

	private final Bme680Mapper mapper;

	private final Map<Integer, Date> register = new HashMap<>();

	private Queue<Bme680Measurement> persistQueue = new ArrayBlockingQueue<Bme680Measurement>(100);

	@Value("${duplicate-check.max-age:60000}")
	private long maximumAgeDuplicateRegister;

	public void registerMeasurement(Bme680MeasurementDto dto) {

		var entity = mapper.map(dto);

		synchronized (this) {
			if (register.get(entity.hashCode()) == null) {
				register.put(entity.hashCode(), new Date());
				if (!persistQueue.offer(entity)) {
					log.warn("Persist queue is full, measurement will be lost");
				}
				log.debug("Service: {}", entity.hashCode());
				// TODO: handle/persist the item asynchronously.
			} else {
				log.debug("Skipping duplicate with hashCode {}", entity.hashCode());
			}
		}

	}

	@Scheduled(fixedDelayString = "${duplicate-check.delay:10000}")
	public void cleanUpRegister() {

		Set<Integer> deletable = new HashSet<>();
		log.debug("Scanning {} items for obsolete items.", register.size());
		synchronized (this) {
			register.keySet().stream().forEach(s -> {
				if (System.currentTimeMillis() - register.get(s).getTime() > maximumAgeDuplicateRegister) {
					deletable.add(s);
				}
			});
			deletable.forEach(s -> register.remove(s));
		}
		if (deletable.size() != 0) {
			log.info("{} entries older than {} seconds were removed from duplicate-check-cache", deletable.size(),
					maximumAgeDuplicateRegister / 1000);
		}
	}
	
	@Scheduled(fixedDelayString = "${measurement.persist.delay:1000}")
	public void persist() {
		
		if(persistQueue.isEmpty()) {
			return;
		}
		
		int count = 0;
		boolean empty = false;
		while(!empty) {
			Bme680Measurement item = persistQueue.poll();
			empty = (item == null);
			if(item != null) {
				// Persist.
				count++;
			}
		}
		log.info("Persisteed {} measurements", count);
	}
	
}
