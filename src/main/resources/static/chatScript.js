function onLoad() {
    var ws = "ws://localhost:8080/chat";
    websocket = new WebSocket(ws);
    websocket.onopen = function (event) {
        onOpen(event);
    };
    websocket.onmessage = function (event) {
        onMessage(event);
    }
}

function onOpen(event) {
    console.log("Connected");
}

function onMessage(event) {
    var message = event.data;

    messages.innerHTML = messages.innerHTML + "<li class = \"message\">" + message +"<\li>";
    messages.scrollTop = messages.scrollHeight;
}


function sendMessage(){
    var message = writer.value;
    writer.value = "";
    websocket.send(message);
}
