import ballerina/jballerina.java;

# Ballerina class mapping for the Java `FULL_CLASS_NAME` CLASS_TYPE.
@java:Binding {
    'class: "FULL_CLASS_NAME"
}
distinct class SIMPLE_CLASS_NAME {

    *java:JObject;

    handle jObj;

    # The init function of the Ballerina class mapping the `FULL_CLASS_NAME` Java CLASS_TYPE.
    #
    # + obj - The `handle` value containing the Java reference of the object.
    function init(handle obj) {
        self.jObj = obj;
    }

    # The function to retrieve the string representation of the Ballerina class mapping the `FULL_CLASS_NAME` Java CLASS_TYPE.
    #
    # + return - The `string` form of the Java object instance.
    function toString() returns string {
        return java:toString(self.jObj) ?: "null";
    }
}
