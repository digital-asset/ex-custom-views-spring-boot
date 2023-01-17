package com.daml.quickstart.iou;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("events")
public class ProjectedEventsController {

    private EventsRepository eventsRepository;

    @Autowired
    public ProjectedEventsController(EventsRepository eventsRepository) {
        this.eventsRepository = eventsRepository;
    }

    @GetMapping("")
    public @ResponseBody List<ProjectedEvent> getEvents() {
        return eventsRepository.all();
    }

    @GetMapping("/count")
    public @ResponseBody Integer countEvents() {
        return eventsRepository.count();
    }

    @GetMapping("/observer/{party}")
    public @ResponseBody List<ProjectedEvent> getEventsByObserver(@PathVariable String party){
        return eventsRepository.byObserver(party);
    }
}
