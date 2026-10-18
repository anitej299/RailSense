from fastapi.testclient import TestClient
from api.main import app

client=TestClient(app)


def test_health_and_routes():
    assert client.get("/health").status_code==200
    assert client.get("/models").status_code==200
    assert client.get("/segments").status_code==200
    assert client.get("/experiments").status_code==200


def test_prediction_validation():
    response=client.post("/predict",json={"values":[[0.0]*8]*4})
    assert response.status_code==422

