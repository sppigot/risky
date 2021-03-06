behaviour-detector
====================

Drift detection
------------------
Using course, heading and speed we have simple criteria for detecting if a position report is a drift candidate:

A vessel is considered to be drifting if 
* its speed is non-zero (&ge;0.25 knots for example) and &lt;20 knots
* its course-over-ground differs from its heading by &ge;45 degrees
* its AIS set is not indicating that it is at anchor or moored

###When did drift start?

Things get trickier when we want to answer this question:

* When did the vessel **start** drifting?

####What's trickier about detecting the start of drift?

* a drift of several hours will probably encounter environmental changes (tide changes, wind changes, current changes both temporally and positionally). This means that our simple drift criteria above may from time to time indicate that a vessel has stopped drifting when it has not.
* if vessel position reports have big time gaps it may be undesirable to indicate that the vessel was drifting for the whole period.
* some ais messages may be corrupted (though this is <0.1% of messages)
* some ais sets are wrongly configured (wrong mmsi particularly)
* need to account for different reporting frequencies (small intervals near terrestrial AIS stations and larger satellite reporting intervals away from them).
* need to buffer position reports in memory or database for every vessel (most likely in-memory because of high data rates)

####Algorithm
The following algorithm is proposed (implemented in [DriftDetectorOperator.java](src/main/java/au/gov/amsa/navigation/DriftDetectorOperator.java):

<img src="src/docs/drift-detection-flow.png?raw=true" />

Define 

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`E` is the maximum time between two drift detections for them to be considered as one drift path.

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`T` is the maximum time that a vessel can stop drifting before breaking a drift path.

Before recording a drift path we require at least two drift detections on the same path.

Now let's introduce some notation that will make the algorithm much more concise to explain.

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`D` is a drift detection, 

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;`N` is a non drifting report.

We now want to process a stream of position reports (any reports out of time order are chucked). Consider a small buffer that we pass reports through (turns out a size of 2 is sufficient) and sometimes drift paths come out the other side. Using the rules below we will see that the buffer only needs to hold a maximum of three elements at a time.

This is how we represent a sequence of reports and how we reference time for the reports:

&nbsp;&nbsp;&nbsp;&nbsp;D<sub>1</sub>N<sub>2</sub> represents a drift detection at time t<sub>1</sub> followed by a non drift detection at time t<sub>2</sub>.

Exiting the buffer we should only see Ds and we indicate the start of a drift path by quoting a D. Here's an example:

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;D'<sub>1</sub>D<sub>2</sub>D'<sub>3</sub>D<sub>4</sub>

A report that is kept in the buffer after being emitted is represented by bolding: <b>D<sub>1</sub></b>.

Note that with the buffer we also need a record of the start time of the current drift path. That start time is reset whenever a D' is emitted from the buffer.

Now suppose we have reports in the buffer. This is how the buffer is handled:

We define the following transformation rules (which are applied repeatedly till no change) for elements in the buffer:

1. N<sub>1</sub>  &#8594; nothing<br/><br/>
2. D<sub>1</sub>D<sub>2</sub>  &#8594; <br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>D<sub>2</sub></b>, emit D'<sub>1</sub>D<sub>2</sub> if t<sub>2</sub> - t<sub>1</sub> &lt; E<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;D<sub>2</sub> otherwise<br/><br/>
3. <b>D<sub>1</sub></b>D<sub>2</sub>  &#8594; <br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>D<sub>2</sub></b>, emit D<sub>2</sub> if t<sub>2</sub> - t<sub>1</sub> &lt; E<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;D<sub>2</sub> otherwise<br/><br/>
4. D<sub>1</sub>N<sub>2</sub>N<sub>3</sub> &#8594; D<sub>1</sub>N<sub>2</sub><br/><br/>
5. <b>D<sub>1</sub></b>N<sub>2</sub>N<sub>3</sub> &#8594; <b>D<sub>1</sub></b>N<sub>2</sub><br/><br/>
6. D<sub>1</sub>N<sub>2</sub>D<sub>3</sub>  &#8594; <br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;D<sub>1</sub>D<sub>3</sub> if t<sub>3</sub> - t<sub>2</sub> &lt; T<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;D<sub>3</sub> otherwise<br/><br/>
7. <b>D<sub>1</sub></b>N<sub>2</sub>D<sub>3</sub>  &#8594; <br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>D<sub>1</sub></b>D<sub>3</sub> if t<sub>3</sub> - t<sub>2</sub> &lt; T<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;D<sub>3</sub> otherwise<br/><br/>

In terms of memory utilization it should be noted that one of these buffers is required per vessel and is retained for the life of the application. Given that 30 to 40 thousand distinct vessels traverse the Australian SRR per year this may be important to optimize.

###Visualizations

For the area around Australia (to about 10 degrees above the equator) there are 3m drift detections per year. When visualizing the drift paths we naturally seek to filter the data somewhat. These are some filtering techniques being used in AMSA visualizations:

* only include drift paths that are longer than 1nm
* only display one point per hour for each drift path

The above filters reduce the 3m points to 211K.
