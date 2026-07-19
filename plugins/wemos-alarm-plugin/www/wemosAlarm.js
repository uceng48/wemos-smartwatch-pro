var exec = require('cordova/exec');

module.exports = {
    start: function (ip, intervalMs, success, error) {
        exec(success, error, 'WemosAlarmPlugin', 'start', [ip, intervalMs]);
    },

    stop: function (success, error) {
        exec(success, error, 'WemosAlarmPlugin', 'stop', []);
    },

    requestBatteryIgnore: function (success, error) {
        exec(success, error, 'WemosAlarmPlugin', 'requestBatteryIgnore', []);
    }
};
